/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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

package com.exactrpo.th2.pcapreader.source.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactrpo.th2.pcapreader.framer.PacketFramer;
import com.exactrpo.th2.pcapreader.packet.IpPacket;
import com.exactrpo.th2.pcapreader.packet.TcpPacket;
import com.exactrpo.th2.pcapreader.packet.TcpPacket.Flag;
import com.exactrpo.th2.pcapreader.source.DataSource;
import com.exactrpo.th2.pcapreader.source.PacketMetadata;
import com.exactrpo.th2.pcapreader.source.PcapStreamId;
import com.exactrpo.th2.pcapreader.source.PcapStreamId.Address;
import com.exactrpo.th2.pcapreader.source.StreamContext;
import com.exactrpo.th2.pcapreader.source.cfg.TcpSourceConfiguration;
import com.exactrpo.th2.pcapreader.source.handler.OnUnprocessedData;
import com.exactrpo.th2.pcapreader.source.handler.StreamHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

public class TcpSource implements DataSource<TcpPacket> {
    public interface OnGap {
        void accept(
                PcapStreamId id,
                PacketMetadata lastKnown,
                PacketMetadata firstAfterGap,
                ByteBuf gapPayload,
                List<PacketMetadata> gapPackets
        );
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpSource.class);
    private static final long SEQ_MASK = 0xFFFFFFFFL;

    private static final long SEQ_INC = SEQ_MASK + 1L;
    private static final long UNKNOWN_NUMBER = -1L;
    private final Map<PcapStreamId, TcpStreamData> tcpStreams = new HashMap<>();

    private Instant lastPacketTimestamp;

    private final TcpSourceConfiguration configuration;
    private final OnGap onGap;

    private final OnUnprocessedData onUnprocessedData;

    public TcpSource(TcpSourceConfiguration configuration, OnGap onGap, OnUnprocessedData onUnprocessedData) {
        this.configuration = Objects.requireNonNull(configuration, "recovery time");
        this.onGap = Objects.requireNonNull(onGap, "on gap handler");
        this.onUnprocessedData = Objects.requireNonNull(onUnprocessedData, "on unprocessed data");
    }

    @Override
    public void advanceTime(Instant time) {
        lastPacketTimestamp = Objects.requireNonNull(time, "time");
    }

    @Override
    public void handlePacket(TcpPacket packet) {
        LOGGER.trace("Processing packet {}", packet);
        lastPacketTimestamp = getArrivalTime(packet);

        checkStreams();

        PcapStreamId pcapStreamId = computeId(packet);
        boolean initiator = isInitiator(packet);
        TcpStreamData tcpStreamData = tcpStreams.computeIfAbsent(pcapStreamId, ignore -> new TcpStreamData(initiator));
        boolean firstPacket = initiator || (packet.isFlagSet(Flag.ACK) && packet.isFlagSet(Flag.SYN));
        if (firstPacket) {
            LOGGER.debug("Resetting {} stream state", pcapStreamId);
            tcpStreamData.reset();
        }
        TcpStreamData oppositeData = tcpStreams.get(swap(pcapStreamId));
        TcpPacket lastOpPacket = oppositeData == null
                ? null
                : oppositeData.getLastPacket();
        long nextExpectedAck = lastOpPacket == null
                ? UNKNOWN_NUMBER
                : calculatedNextSeq(lastOpPacket);
        long nextExpectedSeq = tcpStreamData.nextExpectedSeq();
        LOGGER.trace("Next expected seq number: {}, ack number: {}", nextExpectedSeq, nextExpectedAck);
        if (matchesSeq(packet, nextExpectedSeq)
                && matchesAck(packet, nextExpectedAck)
        ) {
            LOGGER.trace("Packet added: {}", packet);
            tcpStreamData.addPacket(packet);
        } else {
            List<TcpPacket> retransmissionList = tcpStreamData.getRecoveryList();
            LOGGER.trace("Packet cannot be processed right now: {}. Move to waiting queue ({} packet(s))", packet, retransmissionList.size());
            retransmissionList.add(packet);
        }

        processRecoveryList(pcapStreamId, tcpStreamData);
    }

    @Override
    public <T> Map<PcapStreamId, Collection<T>> process(StreamHandler<T> handler) {
        Map<PcapStreamId, Collection<T>> result = new HashMap<>();
        for (Entry<PcapStreamId, TcpStreamData> entry : tcpStreams.entrySet()) {
            PcapStreamId id = entry.getKey();
            TcpStreamData tcpStream = entry.getValue();
            ByteBuf data = tcpStream.getData();
            if (data.isReadable()) {
                LOGGER.trace("Processing data for {}. Readable bytes: {}", id, data.readableBytes());
                Collection<T> res = handler.handle(
                        tcpStream,
                        id,
                        data
                );
                List<PacketMetadata> packetMetadata = tcpStream.readPackets();
                if (!packetMetadata.isEmpty()) {
                    LOGGER.warn("Some packets were read but metadata was not extracted: {}", packetMetadata);
                }
                if (!res.isEmpty()) {
                    result.put(id, res);
                }
            }
            streamCompletion(id, tcpStream, data);
        }
        return result.isEmpty() ? Collections.emptyMap() : result;
    }

    @Override
    public void close() throws Exception {
        LOGGER.info("Closing TCP source with {} streams", tcpStreams.size());
        for (Entry<PcapStreamId, TcpStreamData> entry : tcpStreams.entrySet()) {
            PcapStreamId id = entry.getKey();
            TcpStreamData streamData = entry.getValue();
            ByteBuf data = streamData.getData();
            if (data.isReadable()) {
                ByteBuf remainingData = data.readBytes(data.readableBytes());
                LOGGER.warn("Data left for {} ({} byte(s)): {}",
                        id, remainingData.readableBytes(), ByteBufUtil.hexDump(remainingData));
                onUnprocessedData.accept(
                        id,
                        streamData.readPackets(),
                        remainingData
                );
            }
        }
    }

    private void checkStreams() {
        for (Entry<PcapStreamId, TcpStreamData> entry : tcpStreams.entrySet()) {
            PcapStreamId id = entry.getKey();
            TcpStreamData tcpStreamData = entry.getValue();
            List<TcpPacket> recoveryList = tcpStreamData.getRecoveryList();

            checkLostPackets(id, tcpStreamData, recoveryList);
            checkUnreadData(id, tcpStreamData);
        }
    }

    private void checkUnreadData(PcapStreamId id, TcpStreamData tcpStreamData) {
        int unreadBytes = tcpStreamData.getData().readableBytes();
        if (unreadBytes > configuration.getMaxUnreadBytes().getBytes()) {
            LOGGER.warn("The pcap stream id {} has {} unread bytes. Report and discard", id, unreadBytes);
            ByteBuf unreadData = tcpStreamData.getData().readBytes(unreadBytes);
            List<PacketMetadata> unreadPackets = tcpStreamData.readPackets();
            onUnprocessedData.accept(id, unreadPackets, unreadData);
        }
    }

    private void checkLostPackets(PcapStreamId id, TcpStreamData tcpStreamData, List<TcpPacket> recoveryList) {
        do {
            if (recoveryList.isEmpty()) {
                break;
            }
            TcpPacket lastPacket = tcpStreamData.getLastPacket();
            if (lastPacket == null) {
                break;
            }
            Duration gap = Duration.between(getArrivalTime(lastPacket), lastPacketTimestamp);
            if (gap.compareTo(configuration.getRecoveryTime()) <= 0) {
                break;
            }

            LOGGER.trace("Gap in {} is detected for {}. Looking for next closest packet", gap, id);

            TcpPacket closesPacket = findClosesPacket(recoveryList, lastPacket);
            if (closesPacket == null) {
                LOGGER.trace("No packets found for {}", id);
                break;
            }
            recoveryList.remove(closesPacket);
            try {
                ByteBuf data = tcpStreamData.getData();
                onGap.accept(
                        id,
                        toPacketMetadata(lastPacket),
                        toPacketMetadata(closesPacket),
                        data.isReadable()
                                ? data.readBytes(data.readableBytes())
                                : PacketFramer.EMPTY,
                        tcpStreamData.readPackets()
                );
            } catch (Exception ex) {
                LOGGER.error("Cannot call onGap handler for {}", id, ex);
            }
            LOGGER.trace("Adding closest packet {}", closesPacket);
            tcpStreamData.addPacket(closesPacket);
            processRecoveryList(id, tcpStreamData);
        } while (!Thread.currentThread().isInterrupted());
    }

    private static TcpPacket findClosesPacket(List<TcpPacket> recoveryList, TcpPacket lastPacket) {
        long lastSeqNum = lastPacket.getSequenceNumber();
        long closestDistance = Long.MAX_VALUE;
        TcpPacket closestPacket = null;
        for (TcpPacket packet : recoveryList) {
            long sequenceNumber = packet.getSequenceNumber();
            long to = sequenceNumber < lastSeqNum
                    ? sequenceNumber + SEQ_INC
                    : sequenceNumber;
            long dist = to - lastSeqNum;
            if (dist < closestDistance) {
                closestPacket = packet;
                closestDistance = dist;
            }
        }
        return closestPacket;
    }

    private static boolean matchesSeq(TcpPacket packet, long nextExpectedSeq) {
        return packet.getSequenceNumber() == nextExpectedSeq
                || nextExpectedSeq == UNKNOWN_NUMBER;
    }

    private static boolean matchesAck(TcpPacket packet, long nextExpectedAck) {
        return packet.getAcknowledgementNumber() == nextExpectedAck
                || nextExpectedAck == UNKNOWN_NUMBER
                || packet.isFlagSet(Flag.RST); // no need to check ack if RST set because ack is 0
    }

    private static boolean streamCompletion(PcapStreamId id, TcpStreamData tcpStream, ByteBuf data) {
        if (tcpStream.isCompleted()) {
            int readableBytes = data.readableBytes();
            LOGGER.info("TCP stream for {} is completed. Clear data: {} byte(s) left",
                    id, readableBytes);
            if (data.isReadable()) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Remaining data: {}", ByteBufUtil.hexDump(data));
                }
                data.readerIndex(data.readerIndex() + readableBytes);
                List<PacketMetadata> remainingPackets = tcpStream.readPackets();
                if (!remainingPackets.isEmpty()) {
                    LOGGER.warn("Some packets were not read but the stream is completed: {}", remainingPackets);
                }
            }
        }
        return tcpStream.isCompleted();
    }

    private void processRecoveryList(PcapStreamId pcapStreamId, TcpStreamData tcpStreamData) {
        TcpStreamData oppositeStream = tcpStreams.get(swap(pcapStreamId));
        TcpPacket lastOppositePacket = oppositeStream == null
                ? null
                : oppositeStream.getLastPacket();
        long lastOppositeSeq = lastOppositePacket == null
                ? 0L
                : lastOppositePacket.getSequenceNumber();
        long lastOppositeAck = lastOppositePacket == null
                ? 0L
                : lastOppositePacket.getAcknowledgementNumber();

        List<TcpPacket> recoveryList = tcpStreamData.getRecoveryList();
        if (recoveryList.isEmpty()) {
            return;
        }
        boolean found = true;
        LOGGER.trace("Start processing recovery list for {}", pcapStreamId);
        while (!recoveryList.isEmpty() && found) {
            long nextExpectedSeq = tcpStreamData.nextExpectedSeq();
            boolean foundPacket = false;
            Iterator<TcpPacket> tcpPacketItr = recoveryList.iterator();
            TcpPacket lastPacket = tcpStreamData.getLastPacket();
            long lastSeqNumber = lastPacket == null
                    ? 0L
                    : lastPacket.getSequenceNumber();
            long lastAckNumber = lastPacket == null
                    ? 0L
                    : lastPacket.getAcknowledgementNumber();
            while (tcpPacketItr.hasNext()) {
                TcpPacket packet = tcpPacketItr.next();
                long packetNumber = getPacketNumber(packet);
                LOGGER.trace("Checking packet {}: {}", packetNumber, packet);
                long sequenceNumber = packet.getSequenceNumber();

                long prevSeqNumber = lastPacket == null
                        ? 0L
                        : (lastSeqNumber - lastPacket.getPayload().readableBytes()) % SEQ_MASK;

                LOGGER.trace("Last seq: {}, last ack: {}, prev seq: {}, last op seq: {}, last op ack: {}",
                        lastSeqNumber, lastAckNumber, prevSeqNumber, lastOppositeSeq, lastOppositeAck);

                if (sequenceNumber == nextExpectedSeq) {
                    LOGGER.debug("Reordered packet {} added to stream {}", packetNumber, packet);
                    tcpStreamData.addPacket(packet);
                    tcpPacketItr.remove();
                    foundPacket = true;
                    break;
                }

                if (sequenceNumber < lastSeqNumber && sequenceNumber <= prevSeqNumber) {
                    LOGGER.debug("Drop packet {} retransmission: {}", packetNumber, packet);
                    tcpPacketItr.remove();
                    continue;
                }

                if (sequenceNumber == lastSeqNumber
                        && packet.getAcknowledgementNumber() == lastAckNumber
                        && packet.isFlagSet(Flag.ACK)
                ) {
                    LOGGER.debug("Drop duplicated ack packet {}: {}", packetNumber, packet);
                    tcpPacketItr.remove();
                    continue;
                }

                long diff = sequenceNumber - Math.max(lastSeqNumber, lastOppositeAck);
                if (diff == -1) {
                    LOGGER.debug("Remove tcp keep alive packet {}: {}", packetNumber, packet);
                    tcpPacketItr.remove();
                    continue;
                }
                if (diff > 1) {
                    if (lastOppositeAck == sequenceNumber) {
                        LOGGER.debug("Packet {} with seq {} as last ack {} added to stream: {}",
                                packetNumber, sequenceNumber, lastOppositeAck, packet);
                        tcpStreamData.addPacket(packet);
                        tcpPacketItr.remove();
                        foundPacket = true;
                        break;
                    }
                }
            }
            found = foundPacket;
        }

    }

    private static boolean isInitiator(TcpPacket packet) {
        return packet.isFlagSet(Flag.SYN) && !packet.isFlagSet(Flag.ACK);
    }

    private static PcapStreamId computeId(TcpPacket packet) {
        IpPacket ip = packet.getParent();
        return new PcapStreamId(
                new Address(ip.getSourceIp(), packet.getSourcePort()),
                new Address(ip.getDestinationIp(), packet.getDestinationPort())
        );
    }

    private static PcapStreamId swap(PcapStreamId id) {
        return new PcapStreamId(id.getDst(), id.getSrc());
    }

    private static long getPacketNumber(TcpPacket packet) {
        return packet.getParent() // ip
                .getParent() // ethernet
                .getParent() // pcap
                .getPacketNumber();
    }

    private static Instant getArrivalTime(TcpPacket packet) {
        return packet.getParent() // ip
                .getParent() // ethernet
                .getParent() // pcap
                .getArrivalTimestamp();
    }

    private static class TcpStreamData implements StreamContext {
        private final Deque<MetadataHolder> metadata = new ArrayDeque<>();
        private final List<TcpPacket> recoveryList = new ArrayList<>();
        private final ByteBuf data = Unpooled.buffer();

        private final boolean initiator;

        private TcpPacket lastPacket;

        private int lastReadPosition;

        private boolean completed;

        public TcpStreamData(boolean initiator) {
            this.initiator = initiator;
        }

        public void reset() {
            metadata.clear();
            recoveryList.clear();
            data.clear();
            lastPacket = null;
            lastReadPosition = data.readerIndex();
            completed = false;
        }

        public List<TcpPacket> getRecoveryList() {
            return recoveryList;
        }

        public void addPacket(TcpPacket packet) {
            ByteBuf payload = packet.getPayload();
            if (payload.isReadable()) {
                metadata.add(
                        new MetadataHolder(
                                toPacketMetadata(packet)
                        )
                );
                data.writeBytes(payload.copy());
            }
            completed = packet.isFlagSet(Flag.FIN) || packet.isFlagSet(Flag.RST);
            setLastPacket(packet);
        }

        @Override
        public List<PacketMetadata> readPackets() {
            int start = lastReadPosition;
            int end = start + data.readerIndex();
            if (start == end) {
                // nothing was read
                return Collections.emptyList();
            }
            List<PacketMetadata> readPackets = new ArrayList<>(1);
            int accumulatedPosition = start;
            while (!metadata.isEmpty()) {
                MetadataHolder holder = metadata.poll();
                PacketMetadata meta = holder.getMetadata();
                accumulatedPosition += meta.getPayloadLength() - holder.getReadBytes();
                readPackets.add(meta);
                if (accumulatedPosition > end) {
                    holder.setReadBytes(meta.getPayloadLength() - (accumulatedPosition - end));
                    // only part of the packet was read
                    metadata.addFirst(holder);
                }
                if (accumulatedPosition >= end) {
                    break;
                }
            }
            lastReadPosition = 0;
            data.discardReadBytes();

            return readPackets.isEmpty() ? Collections.emptyList() : readPackets;
        }

        public ByteBuf getData() {
            return data;
        }

        @Override
        public boolean isInitiator() {
            return initiator;
        }

        public TcpPacket getLastPacket() {
            return lastPacket;
        }

        public TcpStreamData setLastPacket(TcpPacket lastPacket) {
            this.lastPacket = lastPacket;
            return this;
        }

        public long nextExpectedSeq() {
            TcpPacket last = lastPacket;
            if (last == null) {
                return UNKNOWN_NUMBER;
            }
            return calculatedNextSeq(last);
        }

        public boolean isCompleted() {
            return completed;
        }
    }

    @NotNull
    private static PacketMetadata toPacketMetadata(TcpPacket packet) {
        return new PacketMetadata(
                getPacketNumber(packet),
                packet.getPayload().readableBytes()
        );
    }

    private static long calculatedNextSeq(TcpPacket last) {
        int payloadSize = last.getPayload().readableBytes();
        if (last.isFlagSet(Flag.FIN) || last.isFlagSet(Flag.SYN)) {
            payloadSize += 1;
        }
        return (payloadSize + last.getSequenceNumber()) & SEQ_MASK;
    }

    private static class MetadataHolder {
        private final PacketMetadata metadata;
        private int readBytes;

        private MetadataHolder(PacketMetadata metadata) {
            this.metadata = metadata;
        }

        public PacketMetadata getMetadata() {
            return metadata;
        }

        public int getReadBytes() {
            return readBytes;
        }

        public void setReadBytes(int readBytes) {
            this.readBytes = readBytes;
        }
    }
}
