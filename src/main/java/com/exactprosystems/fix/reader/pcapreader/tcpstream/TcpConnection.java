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

import com.exactprosystems.fix.reader.pcapreader.InetRoute;
import com.exactprosystems.fix.reader.pcapreader.InetSocketAddress;
import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import com.exactprosystems.fix.reader.pcapreader.raw.TcpEvent;
import com.exactprosystems.fix.reader.pcapreader.state.DefaultInstantDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import io.netty.buffer.Unpooled;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class TcpConnection implements Cloneable {
    private static final Logger logger = LoggerFactory.getLogger(TcpConnection.class);

    private TcpStream tcpStream;
    private long ackNumber;
    private String lastPacketFile;
    private Long lastPacketNum;

    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = DefaultInstantDeserializer.class)
    private Instant lastTimestamp;

    private boolean saveToDb = true;

    private final LinkedList<Pcap> queue = new LinkedList<>();

    private int sourceIp;
    private int destinationIp;
    private int sourcePort;
    private int destinationPort;

    public TcpConnection() {
    }

    TcpConnection(TcpStream tcpStream, long ackNumber, InetRoute route) {
        this.tcpStream = tcpStream;
        if ((ackNumber < 0) || (ackNumber >= TcpStream.SEQUENCE_SPACE)) {
            throw new IllegalArgumentException("Invalid TCPv4 sequence number");
        }
        this.ackNumber = ackNumber;
        this.sourceIp = route.getSourceAddress().getIP();
        this.sourcePort = route.getSourceAddress().getPort();
        this.destinationIp = route.getDestinationAddress().getIP();
        this.destinationPort = route.getDestinationAddress().getPort();
    }

    void processPacket(Pcap newPacket) {
        boolean inWaitList = !queue.isEmpty();
        if (newPacket.header_tcp.syn) {
            shutdown(false);
            ackNumber = newPacket.header_tcp.sequence_number;
        }
        lastTimestamp = newPacket.timestamp;
        queue.offer(newPacket);
        fireAllOrderedPackets();
        advanceTime(newPacket.timestamp);
        if (newPacket.header_tcp.fin || newPacket.header_tcp.rst) {
            shutdown(true);
        }
        if (queue.isEmpty()) {
            if (inWaitList) {
                tcpStream.timeSensitiveConnections.remove(this);
            }
        } else {
            if (!inWaitList) {
                tcpStream.timeSensitiveConnections.add(this);
            }
        }
    }

    void shutdown(boolean remove) {
        flushPacketsIfAnyBefore(Instant.MAX);
        if (remove) {
            InetSocketAddress source = new InetSocketAddress(sourceIp, sourcePort);
            InetSocketAddress destination = new InetSocketAddress(destinationIp, destinationPort);

            tcpStream.connections.remove(InetRoute.of(source, destination));
        }
    }

    void advanceTime(Instant now) {
        flushPacketsIfAnyBefore(now.minus(TcpStream.LOST_PACKET_WAIT_TIMEOUT));
    }

    private void flushPacketsIfAnyBefore(Instant instant) {
        Pcap nextPacket = queue.peekFirst();
        while ((nextPacket != null) && nextPacket.timestamp.isBefore(instant) && !tcpStream.completed) {
            // Queue shouldn't have ready to send packet at this moment.
            fireNearestPacket();
            // Then fire ordered sequence (if any) until next gap is found.
            fireAllOrderedPackets();
            nextPacket = queue.peekFirst();
        }
        if (nextPacket == null) {
            tcpStream.timeSensitiveConnections.remove(this);
        }
    }

    private void fireAllOrderedPackets() {
        Pcap packet = pollNextOrderedPacket();
        while ((packet != null) && !tcpStream.completed) {
            ackNumber = prepareOrderedPacket(packet);
            lastPacketFile = packet.source;
            lastPacketNum = packet.packetNum;
            tcpStream.fireNext(packet, saveToDb);
            packet = pollNextOrderedPacket();
        }
    }

    private void fireNearestPacket() {
        Pcap packet = popNearestPacket();
        ackNumber = prepareAfterGapPacket(packet);
        lastPacketFile = packet.source;
        lastPacketNum = packet.packetNum;
        tcpStream.fireNext(packet, saveToDb);
    }

    private boolean isInOrder(Pcap packet) {
        return TcpStream.compareSequences(packet.header_tcp.sequence_number, ackNumber) <= 0;
    }

    private long prepareOrderedPacket(Pcap packet) {
        long nextSequence = TcpStream.nextSequenceOf(packet);
        packet.status = TcpEvent.TCP_OK;
        if (TcpStream.compareSequences(nextSequence, ackNumber) > 0) {
            if (packet.header_tcp.rst) {
                packet.effectivePayload = Unpooled.EMPTY_BUFFER;
                return nextSequence;
            }

            if (ackNumber == packet.header_tcp.sequence_number) {
                packet.effectivePayload = packet.payload.copy();
            } else {
                long longOffset = TcpStream.distance(packet.header_tcp.sequence_number, ackNumber);
                if (longOffset > Integer.MAX_VALUE) {
                    throw new OutOfMemoryError();
                }
                int offset = (int) longOffset;
                packet.effectivePayload = packet.payload.copy(offset, packet.payloadLength-offset);
            }
            return nextSequence;
        } else {
            packet.header_tcp.bServiseMsg = true;
            if ((packet.payloadLength == 1) && (packet.payload.getByte(0) == 0)) {
                packet.status = TcpEvent.TCP_KEEP_ALIVE;
            } else {
                packet.status = TcpEvent.TCP_RETRANSMISSION;
            }
            packet.effectivePayload = Unpooled.EMPTY_BUFFER;
            return ackNumber;
        }
    }

    private long prepareAfterGapPacket(Pcap packet) {
        long nextSequence = TcpStream.nextSequenceOf(packet);
        packet.status = TcpEvent.TCP_LOST_PACKET;
        if (StringUtils.isNotBlank(lastPacketFile) && lastPacketNum != null) {
            packet.beforeGapPacket = lastPacketFile + " #" + lastPacketNum;
        }
        packet.effectivePayload = packet.payload.copy();
        return nextSequence;
    }

    // Assuming there is a gap between ackNumber and nearest packet
    private Pcap popNearestPacket() {
        long minDistance = queue.stream().reduce(
                Long.MAX_VALUE,
                (distance, packet) -> Math.min(distance, TcpStream.distance(ackNumber, packet.header_tcp.sequence_number)),
                Math::min);
        Iterator<Pcap> it = queue.iterator();
        while (it.hasNext()) {
            Pcap packet = it.next();
            if (minDistance == TcpStream.distance(ackNumber, packet.header_tcp.sequence_number)) {
                it.remove();
                return packet;
            }
        }
        throw new NoSuchElementException();
    }

    private Pcap pollNextOrderedPacket() {
        Iterator<Pcap> it = queue.iterator();
        while (it.hasNext()) {
            Pcap packet = it.next();
            if (isInOrder(packet)) {
                for (Iterator<Pcap> itt = queue.iterator(); ; ) {
                    Pcap previousPacket = itt.next();
                    if (previousPacket == packet) {
                        break;
                    }
                    previousPacket.status = TcpEvent.TCP_OUT_OF_ORDER;
                }
                it.remove();
                return packet;
            }
        }
        return null;
    }

    //Used only for state saving
    public TcpConnection clone() {
        TcpConnection cloned = new TcpConnection();
        cloned.ackNumber = ackNumber;
        cloned.lastPacketFile = lastPacketFile;
        cloned.lastPacketNum = lastPacketNum;
        cloned.lastTimestamp = lastTimestamp;
        cloned.saveToDb = saveToDb;
        cloned.sourceIp = sourceIp;
        cloned.destinationIp = destinationIp;
        cloned.sourcePort = sourcePort;
        cloned.destinationPort = destinationPort;
        for (Pcap pcap : queue) {
            cloned.queue.add(pcap.clone());
        }
        return cloned;
    }

    public long getAckNumber() {
        return ackNumber;
    }

    public LinkedList<Pcap> getQueue() {
        return queue;
    }

    public void setAckNumber(long ackNumber) {
        this.ackNumber = ackNumber;
    }

    @JsonIgnore
    public TcpStream getTcpStream() {
        return tcpStream;
    }

    public void setTcpStream(TcpStream tcpStream) {
        this.tcpStream = tcpStream;
    }

    public Instant getLastTimestamp() {
        return lastTimestamp;
    }

    public void setLastTimestamp(Instant lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }

    public boolean isSaveToDb() {
        return saveToDb;
    }

    public void setSaveToDb(boolean saveToDb) {
        this.saveToDb = saveToDb;
    }

    public int getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(int sourceIp) {
        this.sourceIp = sourceIp;
    }

    @Deprecated
    public void setSourceIp(String sourceIp) {
        this.sourceIp = ParsingUtils.ipToInt(sourceIp);
    }

    public int getDestinationIp() {
        return destinationIp;
    }

    public void setDestinationIp(int destinationIp) {
        this.destinationIp = destinationIp;
    }

    @Deprecated
    public void setDestinationIp(String destinationIp) {
        this.destinationIp = ParsingUtils.ipToInt(destinationIp);
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
    }
}
