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

package com.exactprosystems.fix.reader.pcapreader.state;

import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStream;

import java.util.List;

public class TcpStreamState {
    private List<Connection> timeSensitiveConnections;
    private List<Connection> connections;
    private PacketHandlerState packetHandlerState;
    private Class<? extends TcpStream> clazz;
    private Long savedMessages;

    public TcpStreamState() {
    }

    public TcpStreamState(List<Connection> timeSensitiveConnections,
                          List<Connection> connections,
                          PacketHandlerState packetHandlerState,
                          Class<? extends TcpStream> clazz,
                          Long savedMessages) {
        this.timeSensitiveConnections = timeSensitiveConnections;
        this.connections = connections;
        this.packetHandlerState = packetHandlerState;
        this.clazz = clazz;
        this.savedMessages = savedMessages;
    }

    public List<Connection> getTimeSensitiveConnections() {
        return timeSensitiveConnections;
    }

    public void setTimeSensitiveConnections(List<Connection> timeSensitiveConnections) {
        this.timeSensitiveConnections = timeSensitiveConnections;
    }

    public List<Connection> getConnections() {
        return connections;
    }

    public void setConnections(List<Connection> connections) {
        this.connections = connections;
    }

    public Class<? extends TcpStream> getClazz() {
        return clazz;
    }

    public void setClazz(Class<? extends TcpStream> clazz) {
        this.clazz = clazz;
    }

    public PacketHandlerState getPacketHandlerState() {
        return packetHandlerState;
    }

    public void setPacketHandlerState(PacketHandlerState packetHandlerState) {
        this.packetHandlerState = packetHandlerState;
    }

    public Long getSavedMessages() {
        return savedMessages;
    }

    public void setSavedMessages(Long savedMessages) {
        this.savedMessages = savedMessages;
    }
}
