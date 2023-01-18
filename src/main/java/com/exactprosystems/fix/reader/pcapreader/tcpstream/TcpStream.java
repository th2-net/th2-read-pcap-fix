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

import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.pcapreader.InetRoute;
import com.exactprosystems.fix.reader.pcapreader.InetSocketAddress;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import com.exactprosystems.fix.reader.pcapreader.constants.ProtocolType;
import com.exactprosystems.fix.reader.prometheus.PrometheusMetrics;
import com.exactprosystems.fix.reader.pcapreader.handlers.PacketHandler;
import com.exactprosystems.fix.reader.pcapreader.state.State;
import com.exactprosystems.fix.reader.pcapreader.state.TcpStreamState;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public abstract class TcpStream {
    public static final long SEQUENCE_SPACE = 0x1_00_00_00_00L;
    public static final long SEQUENCE_SPACE_MASK = SEQUENCE_SPACE - 1L;

    public static final Duration LOST_PACKET_WAIT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration TIME_ADVANCEMENT_INTERVAL = Duration.ofMillis(100);

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public final PacketHandler handler;

    protected PcapFileReaderConfiguration configuration;

    public boolean completed = false;

    public HashSet<TcpConnection> timeSensitiveConnections = new HashSet<>();
    public Instant lastReportedTime = Instant.MIN;
    public Instant lastKnownTimestamp = null;
    public long savedMessagesNum = 0;

    public final HashMap<InetRoute, TcpConnection> connections = new HashMap<>();
    protected ProtocolType protocolType;

    public TcpStream(PacketHandler handler, PcapFileReaderConfiguration configuration) {
        if (handler == null) {
            throw new NullPointerException();
        }
        this.handler = handler;
        this.configuration = configuration;
    }

    public void processPacket(Pcap packet, boolean saveToDb) {
        if (completed) {
            throw new IllegalStateException("TcpStream is shut down");
        }

        lastKnownTimestamp = packet.timestamp;
        if (packet.isTCPHeaderExist()) {
            InetRoute inetRoute = routeOf(packet);
            TcpConnection connection = connections.computeIfAbsent(inetRoute,
                    route -> new TcpConnection(this, packet.header_tcp.sequence_number, inetRoute));
            connection.setSaveToDb(saveToDb);
            connection.processPacket(packet);
        } else {
            packet.effectivePayload = packet.payload == null ? Unpooled.EMPTY_BUFFER : packet.payload.copy();
            fireNext(packet, saveToDb);
        }
        advanceTime(false);
    }

    public abstract void closeDeadConnections();

    public void flush() {
        if (lastKnownTimestamp != null) {
            advanceTime(true);
        }
    }

    public void shutdown() {
        if (!completed) {
            connections.values().forEach(key -> key.shutdown(false));
            completed = true;
            timeSensitiveConnections.clear();
            connections.clear();
            handler.onComplete();
        }
    }

    private void advanceTime(boolean force) {
        if (force || lastReportedTime.plus(TIME_ADVANCEMENT_INTERVAL).isBefore(lastKnownTimestamp)) {
            if (!timeSensitiveConnections.isEmpty()) {
                ArrayList<TcpConnection> clonedList = new ArrayList<>(timeSensitiveConnections);
                clonedList.forEach(c -> c.advanceTime(lastKnownTimestamp));
            }
            lastReportedTime = lastKnownTimestamp;
        }
    }

    public void fireNext(Pcap packet, boolean saveToDb) {
        try {
            long messageNum = handler.onNext(packet, saveToDb);
            savedMessagesNum += messageNum;
            PrometheusMetrics.READ_MESSAGES_NUMBER.labels(protocolType.toString()).inc(messageNum);
        } catch (Exception ex) {
            completed = true;
            connections.clear();
            handler.onError(ex);
        }
    }

    public static int compareSequences(long x, long y) {
        // Compare on minimal distance
        if (x == y) {
            return 0;
        }
        long delta = x - y;
        if (delta > SEQUENCE_SPACE / 2) {
            return (delta - SEQUENCE_SPACE) < 0 ? -1 : 1;
        }
        if (delta < -SEQUENCE_SPACE / 2) {
            return (delta + SEQUENCE_SPACE) < 0 ? -1 : 1;
        }
        return delta < 0 ? -1 : 1;
    }

    public static long distance(long from, long to) {
        if (to < from) {
            return (to + SEQUENCE_SPACE) - from;
        }
        return to - from;
    }

    public static long nextSequenceOf(Pcap packet) {
        long length = packet.payloadLength;
        if (packet.header_tcp.syn || packet.header_tcp.fin) {
            ++length;
        }
        return (packet.header_tcp.sequence_number + length) & SEQUENCE_SPACE_MASK;
    }

    public static InetRoute routeOf(Pcap packet) {
        int sourcePort = packet.header_tcp.source_port;
        int destPort = packet.header_tcp.destination_port;
        InetSocketAddress source = new InetSocketAddress(packet.header_ip4.sourceIpRaw, sourcePort);
        InetSocketAddress destination = new InetSocketAddress(packet.header_ip4.destinationIpRaw, destPort);
        return InetRoute.of(source, destination);
    }

    @JsonIgnore
    public PacketHandler getHandler() {
        return handler;
    }

    public HashSet<TcpConnection> getTimeSensitiveConnections() {
        return timeSensitiveConnections;
    }

    public HashMap<InetRoute, TcpConnection> getConnections() {
        return connections;
    }

    public abstract TcpStreamState getState();

    public abstract void loadState(State state) throws IOException;

    public ProtocolType getProtocolType() {
        return protocolType;
    }
}
