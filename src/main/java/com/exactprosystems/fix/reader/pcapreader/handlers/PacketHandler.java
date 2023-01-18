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

import com.exactprosystems.fix.reader.connectionevents.TcpConnectionEvent;
import com.exactprosystems.fix.reader.cradle.Saver;
import com.exactprosystems.fix.reader.eventsender.EventPublisher;
import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import com.exactprosystems.fix.reader.pcapreader.state.PacketHandlerState;
import com.exactprosystems.fix.reader.pcapreader.raw.TcpEvent;

import java.io.IOException;

public interface PacketHandler {
    long onNext(Pcap packet, boolean saveToDb) throws IOException;

    void onError(Throwable t);

    void onComplete();

    PacketHandlerState getState();

    void loadState(PacketHandlerState state) throws IOException;

    Saver getCradleSaver();

    EventPublisher getEventPublisher();

    void printSkippedInfo();

    default void saveTcpEventIfExists(Pcap packet, boolean isClientStream, boolean saveToDb) {
        if (getCradleSaver() == null || !saveToDb) {
            return;
        }

        if (packet.header_tcp != null && packet.header_tcp.syn && !packet.header_tcp.ack) {
            getCradleSaver().saveConnectionEvent(
                    new TcpConnectionEvent(ParsingUtils.intToIp(packet.header_ip4.sourceIpRaw),
                            ParsingUtils.intToIp(packet.header_ip4.destinationIpRaw),
                            packet.header_tcp.source_port,
                            packet.header_tcp.destination_port,
                            TcpConnectionEvent.TcpEventType.CONNECT),
                    packet,
                    isClientStream,
                    saveToDb);
        }

        if (packet.header_tcp != null && (packet.header_tcp.rst || packet.header_tcp.fin)) {
            getCradleSaver().saveConnectionEvent(
                    new TcpConnectionEvent(ParsingUtils.intToIp(packet.header_ip4.sourceIpRaw),
                            ParsingUtils.intToIp(packet.header_ip4.destinationIpRaw),
                            packet.header_tcp.source_port,
                            packet.header_tcp.destination_port,
                            TcpConnectionEvent.TcpEventType.GRACEFUL_DISCONNECT),
                    packet,
                    isClientStream,
                    saveToDb);
        }

        if (TcpEvent.TCP_LOST_PACKET.equals(packet.status)) {
            TcpConnectionEvent tcpConnectionEvent = new TcpConnectionEvent(ParsingUtils.intToIp(packet.header_ip4.sourceIpRaw),
                    ParsingUtils.intToIp(packet.header_ip4.destinationIpRaw),
                    packet.header_tcp.source_port,  // header_tcp must not be null here
                    packet.header_tcp.destination_port,
                    TcpConnectionEvent.TcpEventType.GAP);
            getCradleSaver().saveConnectionEvent(
                    tcpConnectionEvent,
                    packet,
                    isClientStream,
                    saveToDb);

            if (getEventPublisher() != null) {
                getEventPublisher().publishGapEvent(tcpConnectionEvent, packet.source, packet.beforeGapPacket, packet.packetNum);
            }
        }
    }
}
