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

import com.exactprosystems.fix.reader.pcapreader.constants.ProtocolType;

import java.util.Objects;

public class ConnectionAddressState {
    private String IP;
    private int port;
    private ProtocolType protocolType;

    public ConnectionAddressState() {
    }

    public ConnectionAddressState(String IP, int port, ProtocolType protocolType) {
        this.IP = IP;
        this.port = port;
        this.protocolType = protocolType;
    }

    public String getIP() {
        return IP;
    }

    public int getPort() {
        return port;
    }

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public void setIP(String IP) {
        this.IP = IP;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setProtocolType(ProtocolType protocolType) {
        this.protocolType = protocolType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionAddressState that = (ConnectionAddressState) o;
        return port == that.port && Objects.equals(IP, that.IP) && protocolType == that.protocolType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(IP, port, protocolType);
    }
}
