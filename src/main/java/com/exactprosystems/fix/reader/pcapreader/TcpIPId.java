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

import java.util.Objects;

public class TcpIPId {
    private final int sourceIP;
    private final int sourcePort;

    private final int destinationIP;
    private final int destinationPort;

    private int hash = 0;

    public TcpIPId(int sourceIP, int sourcePort, int destinationIP, int destinationPort) {
        this.sourceIP = sourceIP;
        this.sourcePort = sourcePort;
        this.destinationIP = destinationIP;
        this.destinationPort = destinationPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TcpIPId tcpIPId = (TcpIPId) o;
        return sourceIP == tcpIPId.sourceIP && sourcePort == tcpIPId.sourcePort && destinationIP == tcpIPId.destinationIP && destinationPort == tcpIPId.destinationPort;
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = Objects.hash(sourceIP, sourcePort, destinationIP, destinationPort);
        }
        return hash;
    }

    @Override
    public String toString() {
        return ParsingUtils.intToIp(sourceIP) + ':' + sourcePort +
                '_' +
                ParsingUtils.intToIp(destinationIP) + ':' + destinationPort;
    }
}
