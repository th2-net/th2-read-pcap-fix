/*
 * Copyright 2022-2023 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactprosystems.fix.reader.pcapreader.tcpstream;

import com.exactpro.cradle.CradleStorage;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.RawMessageBatch;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactprosystems.fix.reader.cfg.ConnectionAddress;
import com.exactprosystems.fix.reader.cfg.IndividualReaderConfiguration;
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.cradle.AbstractCradleSaver;
import com.exactprosystems.fix.reader.cradle.AbstractMstoreSaver;
import com.exactprosystems.fix.reader.cradle.CradleFixSaver;
import com.exactprosystems.fix.reader.cradle.MstoreFixSaver;
import com.exactprosystems.fix.reader.cradle.Saver;
import com.exactprosystems.fix.reader.eventsender.EventPublisher;
import com.exactprosystems.fix.reader.pcapreader.IpMask;
import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactprosystems.fix.reader.pcapreader.constants.ProtocolType;
import com.exactprosystems.fix.reader.pcapreader.handlers.FixTcpPacketHandler;
import com.exactprosystems.fix.reader.pcapreader.state.ConnectionAddressState;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;

import javax.annotation.Nullable;

public class TcpStreamFactory {
    private static final Logger logger = LoggerFactory.getLogger(TcpStreamFactory.class);

    private PcapFileReaderConfiguration configuration;
    private IndividualReaderConfiguration individualReaderConfiguration;
    private EventPublisher eventPublisher;

    private FixTcpStream fixTcpStream;

    private List<Saver> savers = new ArrayList<>();

    private Set<ProtocolType> protocols;

    private Map<Pair<IpMask, Integer>, TcpStream> tcpStreams = new HashMap<>();
    private Map<Pair<IpMask, Integer>, String> streamNames = new HashMap<>();

    private Map<String, List<ConnectionAddressState>> streamNameConnectionAddressesMap = new HashMap<>();

    private final long maxBatchSize;

    private Map<Pair<Pair<String, Integer>, Pair<String, Integer>>, Integer> unknownAddresses;


    private List<TcpStream> streams = new ArrayList<>();

    public TcpStreamFactory(PcapFileReaderConfiguration configuration, IndividualReaderConfiguration individualReaderConfiguration,
                            CradleStorage storage,
                            MessageRouter<RawMessageBatch> messageRouter, MessageRouter<EventBatch> eventRouter,
                            EventPublisher eventPublisher,
                            long maxBatchSize) {
        this.configuration = configuration;
        this.individualReaderConfiguration = individualReaderConfiguration;
        this.eventPublisher = eventPublisher;
        this.protocols = EnumSet.noneOf(ProtocolType.class);
        this.maxBatchSize = maxBatchSize;
        Saver fixSaver = configuration.isUseMstore() ? new MstoreFixSaver(messageRouter, eventRouter, configuration, this)  :
                new CradleFixSaver(storage, configuration, this);
        savers.add(fixSaver);

        fixTcpStream = new FixTcpStream(new FixTcpPacketHandler(
                fixSaver,
                configuration, this), configuration);
        streams.add(fixTcpStream);

        unknownAddresses = new HashMap<>();


        this.initTcpStreams(fixTcpStream);
    }

    private void initTcpStreams(FixTcpStream fixTcpStream) {
        for (ConnectionAddress connectionAddress : individualReaderConfiguration.getConnectionAddresses()) {
            try {
                String IP = connectionAddress.getIP().strip();
                int port = connectionAddress.getPort();
                ProtocolType protocolType = ProtocolType.valueOf(connectionAddress.getProtocol());
                String streamName = connectionAddress.getStreamName();

                IpMask ipMask = IpMask.createFromString(IP);
                Pair<IpMask, Integer> pair = Pair.of(ipMask, port);
                if (StringUtils.isNotBlank(streamName)) {
                    streamNames.put(pair, streamName);
                    streamNameConnectionAddressesMap.computeIfAbsent(streamName, ign -> new ArrayList<>())
                            .add(new ConnectionAddressState(IP, port, protocolType));
                }
                if (protocolType == ProtocolType.FIX) {
                    tcpStreams.put(pair, fixTcpStream);
                } else {
                    throw new IllegalArgumentException("Unsupported protocol " + protocolType);
                }
            } catch (Exception e) {
                logger.error("Unable to parse connection address {}", connectionAddress, e);
            }
        }
        if (tcpStreams.isEmpty()) {
            throw new IllegalStateException("No TCP stream was created");
        }
    }

    public void closeDeadConnections() {
        tcpStreams.forEach((key, value) -> value.closeDeadConnections());
    }

    public void printSkippedInfo() {
        tcpStreams.forEach((key, value) -> value.getHandler().printSkippedInfo());
    }

    public void triggerSavers() {
        if (configuration.isUseMstore()) {
            if (configuration.isSortTh2Messages()) {
                for (Saver saver : savers) {
                    if (saver instanceof AbstractMstoreSaver) {
                        ((AbstractMstoreSaver) saver).triggerSorter();
                    }
                }
            }
            return;
        }

        for (Saver saver : savers) {
            if (saver instanceof AbstractCradleSaver) {
                ((AbstractCradleSaver) saver).saveAllBatchesAndRenew();
            }
        }
    }

    public void triggerSaversAndWait() throws InterruptedException {
        if (configuration.isUseMstore()) {
            return;
        }

        for (Saver saver : savers) {
            if (saver instanceof AbstractCradleSaver) {
                ((AbstractCradleSaver) saver).saveAllBatchesAndRenew();
                ((AbstractCradleSaver) saver).waitAllFutures();
            }
        }
    }

    public TcpStream getTcpStreamForAddress(int sourceIP, int sourcePort, int destinationIP, int destinationPort) {
        return getStreamForAddress(sourceIP, sourcePort, destinationIP, destinationPort, tcpStreams);
    }

    private TcpStream getStreamForAddress(int sourceIP, int sourcePort, int destinationIP, int destinationPort, Map<Pair<IpMask, Integer>, TcpStream> streams) {
        Pair<Integer, Integer> sourcePair = Pair.of(sourceIP, sourcePort);
        Pair<Integer, Integer> destPair = Pair.of(destinationIP, destinationPort);

        if (streams.isEmpty() && logger.isDebugEnabled()) {
            Pair<Pair<String, Integer>, Pair<String, Integer>> pair = Pair.of(Pair.of(ParsingUtils.intToIp(sourceIP), sourcePort), Pair.of(ParsingUtils.intToIp(destinationIP), destinationPort));
            Integer skipped = unknownAddresses.getOrDefault(Pair.of(sourcePair, destPair), 0);
            unknownAddresses.put(pair, skipped+1);
            return null;
        }

        TcpStream tcpStream = findMatch(sourcePair, streams);
        if (tcpStream == null) {
            tcpStream = findMatch(destPair, streams);
        }

        if (tcpStream != null) {
            protocols.add(tcpStream.getProtocolType());
            return tcpStream;
        }

        if (logger.isDebugEnabled()) {
            Pair<Pair<String, Integer>, Pair<String, Integer>> pair = Pair.of(Pair.of(ParsingUtils.intToIp(sourceIP), sourcePort), Pair.of(ParsingUtils.intToIp(destinationIP), destinationPort));
            Integer skipped = unknownAddresses.getOrDefault(pair, 0);
            unknownAddresses.put(pair, skipped + 1);
        }
        return null;
    }

    public void printUnknownAddresses() {
        if (logger.isDebugEnabled()) {
            for (Map.Entry<Pair<Pair<String, Integer>, Pair<String, Integer>>, Integer> pairIntegerEntry : unknownAddresses.entrySet()) {
                logger.debug("Unknown address {}:{} -> {}:{} skipped {} packets",
                        pairIntegerEntry.getKey().getLeft().getLeft(), pairIntegerEntry.getKey().getLeft().getRight(),
                        pairIntegerEntry.getKey().getRight().getLeft(), pairIntegerEntry.getKey().getRight().getRight(),
                        pairIntegerEntry.getValue());
            }
        }
        unknownAddresses.clear();
    }

    public String getStreamName(int IP, int port) {
        return findMatch(Pair.of(IP, port), streamNames);
    }

    public Collection<String> getStreamNames(){
        return streamNames.values();
    }

    public boolean isClientStream(int sourceIP, int sourcePort) {
        return findMatch(Pair.of(sourceIP, sourcePort), tcpStreams) == null;
    }

    public void flushAllStreams() {
        streams.forEach(TcpStream::flush);
    }

    public void shutdownAllStreams() {
        streams.forEach(TcpStream::shutdown);
    }

    public List<TcpStream> getStreams() {
        return streams;
    }

    public List<Saver> getSavers() {
        return savers;
    }

    public List<Pair<ProtocolType, Long>> getSavedMessagesInfo() {
        List<Pair<ProtocolType, Long>> res = new ArrayList<>();
        res.add(Pair.of(ProtocolType.FIX, fixTcpStream.savedMessagesNum));

        return res;
    }

    public void resetSavedInfo() {
        fixTcpStream.savedMessagesNum = 0;
        protocols.clear();
    }

    public Set<ProtocolType> getProtocols() {
        return protocols;
    }

    public PcapFileReaderConfiguration getConfiguration() {
        return configuration;
    }

    public EventPublisher getEventPublisher() {
        return eventPublisher;
    }

    public long getMaxBatchSize() {
        return maxBatchSize;
    }

    public Map<String, List<ConnectionAddressState>> getStreamNameConnectionAddressesMap() {
        return streamNameConnectionAddressesMap;
    }

    public void setStreamNameConnectionAddressesMap(Map<String, List<ConnectionAddressState>> streamNameConnectionAddressesMap) {
        this.streamNameConnectionAddressesMap = streamNameConnectionAddressesMap;
    }

    @Nullable
    private static <T> T findMatch(Pair<Integer, Integer> sourcePair, Map<Pair<IpMask, Integer>, T> map) {
        int ip = sourcePair.getLeft();
        int port = sourcePair.getRight();
        for (Entry<Pair<IpMask, Integer>, T> entry : map.entrySet()) {
            var maskAndPortPair = entry.getKey();
            IpMask mask = maskAndPortPair.getLeft();
            int actualPort = maskAndPortPair.getRight();
            if (mask.matches(ip) && port == actualPort) {
                return entry.getValue();
            }
        }
        return null;
    }
}
