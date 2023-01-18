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

package com.exactprosystems.fix.reader.pcapreader.handlers;

import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.cradle.Saver;
import com.exactprosystems.fix.reader.eventsender.EventPublisher;
import com.exactprosystems.fix.reader.fix.FIXReader;
import com.exactprosystems.fix.reader.pcapreader.InetRoute;
import com.exactprosystems.fix.reader.pcapreader.InetSocketAddress;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import com.exactprosystems.fix.reader.prometheus.PrometheusMetrics;
import com.exactprosystems.fix.reader.pcapreader.raw.Tcp;
import com.exactprosystems.fix.reader.pcapreader.raw.TcpEvent;
import com.exactprosystems.fix.reader.pcapreader.state.FixReaderInternalState;
import com.exactprosystems.fix.reader.pcapreader.state.PacketHandlerState;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStream;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStreamFactory;
import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FixTcpPacketHandler implements PacketHandler {
    private static final Logger log = LoggerFactory.getLogger(FixTcpPacketHandler.class);

    private final Saver saver;
    private final PcapFileReaderConfiguration configuration;
    private final Map<InetRoute, FIXReader> fixReaders = new HashMap<>();
    private final TcpStreamFactory tcpStreamFactory;
    private final List<Long> skippedPackets;

    public FixTcpPacketHandler(Saver saver, PcapFileReaderConfiguration configuration, TcpStreamFactory tcpStreamFactory) {
        this.saver = saver;
        this.configuration = configuration;
        this.tcpStreamFactory = tcpStreamFactory;
        this.skippedPackets = new ArrayList<>();
    }


    @Override
    public long onNext(Pcap packet, boolean saveToDb) throws IOException {
        log.debug("#{}", packet.packetNum);

        boolean restoreMode = false;
        if (TcpEvent.TCP_LOST_PACKET.equals(packet.status)) {
            PrometheusMetrics.GAP_NUMBER.labels("FIX").inc();
            log.warn("Packet lost {}. Restore procedure will be started", packet.packetNum);
            restoreMode = true;
        }
        Tcp tcp = packet.header_tcp;
        if (tcp == null) {
            log.info("Skip #{}: Not TCP", packet.packetNum);
            return 0;
        }
        InetRoute route = TcpStream.routeOf(packet);
        if (tcp.syn && !tcp.ack) {
            String streamName = tcpStreamFactory.getStreamName(route.getDestinationAddress().getIP(), route.getDestinationAddress().getPort());
            fixReaders.put(route, createFixReader(StringUtils.isNotBlank(streamName) ? streamName : "FIX_" + packet.getTCPIPId(false), StringUtils.isNotBlank(streamName)));
        }

        boolean isClientStream = true;
        FIXReader reader = fixReaders.get(route);
        if (reader == null) {
            reader = fixReaders.get(route.swap());
            if (reader != null)
                isClientStream = false;
            else {
                log.warn("#{}: Missed SYN for route {}. Start of restoring ", packet.packetNum, route);
                if (tcpStreamFactory.isClientStream(route.getSourceAddress().getIP(), route.getSourceAddress().getPort())) {
                    String streamName = tcpStreamFactory.getStreamName(route.getDestinationAddress().getIP(), route.getDestinationAddress().getPort());
                    reader = createFixReader(StringUtils.isNotBlank(streamName) ? streamName : "FIX_" + packet.getTCPIPId(false), StringUtils.isNotBlank(streamName));
                    fixReaders.put(route, reader);
                } else {
                    String streamName = tcpStreamFactory.getStreamName(route.getSourceAddress().getIP(), route.getSourceAddress().getPort());
                    reader = createFixReader(StringUtils.isNotBlank(streamName) ? streamName : "FIX_" + packet.getTCPIPId(true), StringUtils.isNotBlank(streamName));
                    fixReaders.put(route.swap(), reader);
                    isClientStream = false;
                }
                restoreMode = true;
            }
        }

        saveTcpEventIfExists(packet, isClientStream, saveToDb);

        ByteBuf payload = packet.effectivePayload;
        if (payload == null || payload.readableBytes() == 0) {
            skippedPackets.add(packet.packetNum);
            return 0;
        }

        long messageCount = 0;
        try {
            if (restoreMode || reader.isRestoreMode()) {
                List<Pcap> pcaps = reader.restoreStream(isClientStream, packet);
                if (!pcaps.isEmpty()) {
                    log.info("FIX gap restoring complete. First packet: {} #{}", pcaps.get(0).source, pcaps.get(0).packetNum);
                    for (Pcap pcap : pcaps) {
                        messageCount += reader.readFix(pcap.effectivePayload,
                                tcpStreamFactory.isClientStream(pcap.header_ip4.sourceIpRaw, pcap.header_tcp.source_port),
                                pcap, saveToDb);
                    }
                    pcaps.clear();
                }
            } else {
                messageCount += reader.readFix(payload, isClientStream, packet, saveToDb);
            }
        } catch (Exception e) {
            log.error("Unable to read fix frames: {} #{}", packet.source, packet.packetNum, e);
        }

        return messageCount;
    }

    @Override
    public void onError(Throwable t) {
        log.error("Error while processing FIX stream", t);
    }

    @Override
    public void onComplete() {
        log.info("Handler {} complete", this.getClass());
    }

    @Override
    public void printSkippedInfo() {
        if (!skippedPackets.isEmpty()) {
            log.debug("Skip packets with empty payload {}", skippedPackets);
            skippedPackets.clear();
        }
    }

    public Map<InetRoute, FIXReader> getFixReaders() {
        return fixReaders;
    }

    @Override
    public PacketHandlerState getState() {
        List<FixReaderInternalState> fixReaderInternalStateList = new ArrayList<>();
        Map<InetRoute, FIXReader> fixReadersTemp = this.fixReaders;
        for(Map.Entry<InetRoute, FIXReader> entry :  fixReadersTemp.entrySet()) {
            fixReaderInternalStateList.add(new FixReaderInternalState(
                    entry.getKey().getSourceAddress().getIP(),
                    entry.getKey().getSourceAddress().getPort(),
                    entry.getKey().getDestinationAddress().getIP(),
                    entry.getKey().getDestinationAddress().getPort(),
                    entry.getValue().getMessageAssemblyMap() == null ? null : Map.ofEntries(entry.getValue().getMessageAssemblyMap().entrySet().toArray(new Map.Entry[0])),
                    entry.getValue().isSaveToDb(),
                    entry.getValue().isRestoreMode(),
                    entry.getValue().getPacketsToRestore()
            ));
        }
        return new PacketHandlerState(fixReaderInternalStateList, this.getClass());
    }

    @Override
    public void loadState(PacketHandlerState state) throws IOException {
        Map<InetRoute, FIXReader> fixReadersTemp = this.fixReaders;
        for(FixReaderInternalState fixReaderInternalState : state.getFixReaderInternalStateList()){
            String streamName = tcpStreamFactory.getStreamName(fixReaderInternalState.getDestinationHost(), fixReaderInternalState.getDestinationPort());
            FIXReader reader = this.createFixReader(StringUtils.isNotBlank(streamName) ? streamName : "FIX_" + fixReaderInternalState.getTCPIPId(), StringUtils.isNotBlank(streamName));
            fixReadersTemp.put(InetRoute.of(
                    new InetSocketAddress(fixReaderInternalState.getSourceHost(), fixReaderInternalState.getSourcePort()),
                    new InetSocketAddress(fixReaderInternalState.getDestinationHost(), fixReaderInternalState.getDestinationPort())
            ), reader);

            reader.setSaveToDb(fixReaderInternalState.isSaveToDb());
            reader.setRestoreMode(fixReaderInternalState.isRestoreMode());
            reader.setPacketsToRestore(fixReaderInternalState.getPacketsToRestore());

            for (Map.Entry<Integer, String> entry : fixReaderInternalState.getMessageMap().entrySet()) {
                reader.getMessageAssemblyMap().put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public Saver getCradleSaver() {
        return saver;
    }

    @Override
    public EventPublisher getEventPublisher() {
        return tcpStreamFactory.getEventPublisher();
    }

    public FIXReader createFixReader(String streamName, boolean useStaticStreamName) throws IOException {
        return new FIXReader(saver, configuration, streamName, useStaticStreamName);
    }

}
