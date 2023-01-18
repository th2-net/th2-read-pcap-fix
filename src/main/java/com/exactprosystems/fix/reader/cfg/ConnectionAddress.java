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

package com.exactprosystems.fix.reader.cfg;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConnectionAddress {

    @JsonProperty("IP")
    private String IP;
    @JsonProperty("port")
    private int port;
    @JsonProperty("protocol")
    private String protocol;
    @JsonProperty("stream_name")
    private String streamName;

    public String getIP() {
        return IP;
    }

    public void setIP(String IP) {
        this.IP = IP;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ConnectionAddress{");
        sb.append("IP='").append(IP).append('\'');
        sb.append(", port=").append(port);
        sb.append(", protocol='").append(protocol).append('\'');
        sb.append(", streamName='").append(streamName).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
