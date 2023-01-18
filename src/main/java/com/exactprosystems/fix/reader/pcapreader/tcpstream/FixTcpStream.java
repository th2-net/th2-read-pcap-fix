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
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.fix.FIXReader;
import com.exactprosystems.fix.reader.pcapreader.constants.ProtocolType;
import com.exactprosystems.fix.reader.pcapreader.handlers.FixTcpPacketHandler;
import com.exactprosystems.fix.reader.pcapreader.handlers.PacketHandler;
import com.exactprosystems.fix.reader.pcapreader.state.Connection;
import com.exactprosystems.fix.reader.pcapreader.state.PacketHandlerState;
import com.exactprosystems.fix.reader.pcapreader.state.State;
import com.exactprosystems.fix.reader.pcapreader.state.TcpStreamState;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FixTcpStream extends TcpStream{

    private final Duration fixDeathIntervalDuration;


    public FixTcpStream(PacketHandler handler, PcapFileReaderConfiguration configuration) {
        super(handler, configuration);
        this.protocolType = ProtocolType.FIX;
        fixDeathIntervalDuration = Duration.ofMillis(configuration.getFixConnectionDeathInterval());
    }

    @Override
    public void closeDeadConnections() {
        if (lastKnownTimestamp == null) {
            return;
        }
        Instant lastLiveValue = lastKnownTimestamp.minus(fixDeathIntervalDuration);

        List<InetRoute> toDelete = new ArrayList<>();

        for (Map.Entry<InetRoute, TcpConnection> entry : connections.entrySet()) {
            if (entry.getValue().getLastTimestamp().isBefore(lastLiveValue)) {
                toDelete.add(entry.getKey());
            }
        }

        FixTcpPacketHandler packetHandler = (FixTcpPacketHandler) handler;
        Map<InetRoute, FIXReader> fixReaders = packetHandler.getFixReaders();


        toDelete.forEach(key -> {
            connections.remove(key);
            fixReaders.remove(key);
        });
    }

    @Override
    public TcpStreamState getState() {
        List<Connection> connections = new ArrayList<>();
        List<Connection> myTimeSensitiveConnections = new ArrayList<>();
        this.getConnections().forEach((key, value) -> {
            Connection connection = new Connection(key.getSourceAddress().getIP(), key.getSourceAddress().getPort(),
                    key.getDestinationAddress().getIP(), key.getDestinationAddress().getPort(), value.clone());
            connections.add(connection);
            if (this.getTimeSensitiveConnections().contains(value)) {
                myTimeSensitiveConnections.add(connection);
            }
        });


        PacketHandlerState state = this.getHandler().getState();

        TcpStreamState tcpStreamState = new TcpStreamState(myTimeSensitiveConnections, connections, state, this.getClass(), this.savedMessagesNum);

        return tcpStreamState;
    }

    @Override
    public void loadState(State rawState) throws IOException {
        FixTcpPacketHandler tcpPacketHandler = (FixTcpPacketHandler) this.getHandler();
        TcpStreamState state = null;

        Optional<TcpStreamState> tcpStreamStateOptional = rawState.getTcpStreamStates()
                .stream()
                .filter(key -> key.getClazz().equals(this.getClass()))
                .findFirst();

        if (tcpStreamStateOptional.isEmpty()) {
            return;
        }

        state = tcpStreamStateOptional.get();

        tcpPacketHandler.loadState(state.getPacketHandlerState());

        HashMap<InetRoute, TcpConnection> connections = this.getConnections();
        state.getConnections().forEach(key -> connections.put(InetRoute.of(
                        new InetSocketAddress(key.getSourceHost(), key.getSourcePort()),
                        new InetSocketAddress(key.getDestinationHost(), key.getDestinationPort())),
                key.getConnection()));

        state.getTimeSensitiveConnections().forEach(key -> timeSensitiveConnections.add(connections.get(InetRoute.of(
                new InetSocketAddress(key.getSourceHost(), key.getSourcePort()),
                new InetSocketAddress(key.getDestinationHost(), key.getDestinationPort())))));

        connections.forEach((key, value) -> value.setTcpStream(this));

        this.savedMessagesNum = state.getSavedMessages();
    }
}
