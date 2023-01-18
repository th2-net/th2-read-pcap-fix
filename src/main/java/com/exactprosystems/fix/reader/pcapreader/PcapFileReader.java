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
package com.exactprosystems.fix.reader.pcapreader;


import com.exactprosystems.fix.reader.cfg.IndividualReaderConfiguration;
import com.exactprosystems.fix.reader.eventsender.EventPublisher;
import com.exactprosystems.fix.reader.eventsender.FileEventType;
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.pcapreader.raw.Ip4;
import com.exactprosystems.fix.reader.pcapreader.raw.Tcp;
import com.exactprosystems.fix.reader.pcapreader.state.StateUtils;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStream;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStreamFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PcapFileReader extends FileReader {
    private final static Logger logger = LoggerFactory.getLogger(PcapFileReader.class);

    private PcapFileReaderConfiguration configuration;
    private IndividualReaderConfiguration individualReaderConfiguration;
    private TcpStreamFactory tcpStreamFactory;
    //identification --> <SourcePort, DestinationPort>
    private final Map<String, Pair<Integer, Integer>> ports;
    private final StateUtils stateUtils;

    public PcapFileReader(TcpStreamFactory tcpStreamFactory,
                          String fileType,
                          PcapFileReaderConfiguration configuration,
                          IndividualReaderConfiguration individualReaderConfiguration,
                          StateUtils stateUtils) {
        super(fileType);
        this.configuration = configuration;
        this.individualReaderConfiguration = individualReaderConfiguration;
        this.tcpStreamFactory = tcpStreamFactory;
        ports = new HashMap<>();
        this.stateUtils = stateUtils;
    }

    public Instant getFirstPacketTimestamp(PacketSource source, String dataID, boolean close) {
        InputStream inputStream = source.getInputStream();

        PcapReader pcapReader = new PcapReader(tcpStreamFactory);
        try {
            boolean fileReady;
            try {
                fileReady = pcapReader.openOffline(inputStream, dataID, source.getCaptureDay(), configuration.getTcpdumpSnapshotLength());
            } catch (IOException exception) {
                logger.error("Failed to process file: {} due to I/O error: {}",
                        source.getSourceName(), exception.getMessage());
                return null;
            }

            if (fileReady) {
                try {
                    Pcap packet = pcapReader.readPacket(false);
                    return packet == null ? null : packet.timestamp;
                } catch (IOException e) {
                    logger.error(String.format("IO error occurred during processing of PCAP stream '%s'.", dataID), e);
                }
            }
        } finally {
            if (close) {
                try {
                    pcapReader.close();
                } catch (IOException e) {
                    logger.error("Error while closing pcap reader", e);
                }
            }
        }
        return null;
    }

    @Override
    public ParseResult readAndParseAndStoreToDb(PacketSource source,
                                                long checkpoint,
                                                long offset,
                                                Long offsetFromDb,
                                                EventPublisher eventPublisher,
                                                Instant previousFileLastPacketTimestamp,
                                                Long stopAfter,
                                                String stopFile) {
        if (source == null) {
            return ParseResult.ZERO;
        }

        long t0 = System.currentTimeMillis();
        boolean success = true;

        if (Thread.currentThread().isInterrupted()) {
            logger.info("Activity cancelled");
            return ParseResult.ZERO;
        }

        Instant firstPacketTimestamp = null;
        Instant lastPacketTimestamp = null;
        long bytesRead = 0;
        long packetsRead = 0;
        String sourceName = source.getSourceName();

        InputStream inputStream = source.getInputStream();

        try (PcapReader pcapReader = new PcapReader(tcpStreamFactory)) {
            boolean fileReady;
            try {
                fileReady = pcapReader.openOffline(inputStream, sourceName, source.getCaptureDay(), configuration.getTcpdumpSnapshotLength());
            } catch (IOException exception) {
                logger.error("Failed to process file: {} due to I/O error: {}",
                        sourceName, exception.getMessage());
                return ParseResult.ZERO;
            }

            if (fileReady) {
                long numReadPackets = 0; // Number of packets read by PcapReader
                try {
                    Pcap packet;
                    while ((packet = pcapReader.readPacket(numReadPackets++ < offset)) != null) {
                        if (numReadPackets == 1) {
                            firstPacketTimestamp = packet.timestamp;
                            if (previousFileLastPacketTimestamp != null) {
                                Duration between = Duration.between(previousFileLastPacketTimestamp, firstPacketTimestamp);
                                if (between.isNegative() && between.abs().compareTo(Duration.ofMillis(configuration.getPossibleTimeWindowBetweenFiles())) > 0) {
                                    if (eventPublisher != null) {
                                        eventPublisher.publishExceptionEvent(sourceName, FileEventType.ERROR, "File contains packets from past");
                                    }
                                    logger.error("{}:#{} timestamp: {}. Previous file last packet timestamp: {}",
                                            sourceName, packet.packetNum, packet.timestamp, previousFileLastPacketTimestamp);
                                    return new ParseResult(Collections.emptyList(), Collections.emptyList(), 0L, 0L, false, null, previousFileLastPacketTimestamp);
                                }
                            }

                        }
                        lastPacketTimestamp = packet.timestamp;
                        if (numReadPackets <= offset) {
                            continue;
                        }
                        packet.source = sourceName;
                        if (Thread.currentThread().isInterrupted()) {
                            logger.info("Activity cancelled");
                            return ParseResult.ZERO; // TODO calculate
                        }

                        if (packet.isTCPHeaderExist()) {
                            TcpStream stream = tcpStreamFactory
                                    .getTcpStreamForAddress(packet.header_ip4.sourceIpRaw,
                                            packet.header_tcp.source_port,
                                            packet.header_ip4.destinationIpRaw,
                                            packet.header_tcp.destination_port);

                            if (stream != null) {
                                stream.processPacket(packet, offsetFromDb == null || offsetFromDb < offset || offsetFromDb < numReadPackets);
                            }
                        }
                        bytesRead += packet.headerLength + (packet.data == null ? 0 : packet.dataLength);

                        if (numReadPackets % configuration.getDeadConnectionsScanInterval() == 0L) {
                            tcpStreamFactory.closeDeadConnections();
                        }
                        if (configuration.isWriteState() && numReadPackets % checkpoint == 0L) {
                            stateUtils.saveState(tcpStreamFactory, sourceName, numReadPackets);
                        }
                        if (stopAfter != null && numReadPackets == stopAfter && Objects.equals(stopFile, source.getSourceName())) {
                            return null;
                        }
                    }
                } catch (Exception ex) {
                    String s = String.format("IO error occurred during processing of PCAP stream '%s' #%d.", sourceName, numReadPackets);
                    logger.error(s, ex);
                    if (eventPublisher != null) {
                        eventPublisher.publishEvent(sourceName,
                                FileEventType.ERROR,
                                t0,
                                System.currentTimeMillis(),
                                bytesRead,
                                new ArrayList<>(tcpStreamFactory.getProtocols()),
                                s);
                    }
                    success = false;
                } finally {
                    packetsRead += numReadPackets;
                    tcpStreamFactory.flushAllStreams();
                }
            }
        } catch (IOException e) {
            logger.error("Error while closing PcapReader", e);
        }
        return new ParseResult(
                tcpStreamFactory.getSavedMessagesInfo(),
                new ArrayList<>(tcpStreamFactory.getProtocols()),
                bytesRead + PcapReader.PCAP_GLOBAL_HEADER_LENGTH,
                packetsRead,
                success,
                firstPacketTimestamp,
                lastPacketTimestamp
        );
    }

    @Override
    public void close() {
        // TODO add isClosed field. Check closed state in read methods.
        tcpStreamFactory.shutdownAllStreams();
    }

    void handlePacket(Pcap packet) {
        logger.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        logger.info("Packet #{}", packet.packetNum);

        Ip4 ip4 = packet.header_ip4;
        if (ip4 != null)
            ip4.print();

        Tcp tcp = packet.header_tcp;
        if (tcp != null)
            tcp.print();
    }
}
