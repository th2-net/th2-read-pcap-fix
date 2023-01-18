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

import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpConnection;

public class Connection {
    private int sourceHost;
    private int sourcePort;

    private int destinationHost;
    private int destinationPort;

    private TcpConnection connection;

    public Connection() {
    }

    public Connection(int sourceHost,
                      int sourcePort,
                      int destinationHost,
                      int destinationPort,
                      TcpConnection connection) {
        this.sourceHost = sourceHost;
        this.sourcePort = sourcePort;
        this.destinationHost = destinationHost;
        this.destinationPort = destinationPort;
        this.connection = connection;
    }

    public int getSourceHost() {
        return sourceHost;
    }

    public void setSourceHost(int sourceHost) {
        this.sourceHost = sourceHost;
    }

    @Deprecated
    public void setSourceHost(String sourceHost) {
        this.sourceHost = ParsingUtils.ipToInt(sourceHost);
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }

    public int getDestinationHost() {
        return destinationHost;
    }

    public void setDestinationHost(int destinationHost) {
        this.destinationHost = destinationHost;
    }

    @Deprecated
    public void setDestinationHost(String destinationHost) {
        this.destinationHost = ParsingUtils.ipToInt(destinationHost);
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
    }

    public TcpConnection getConnection() {
        return connection;
    }

    public void setConnection(TcpConnection connection) {
        this.connection = connection;
    }
}
