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

import com.exactpro.th2.common.grpc.Direction;
import com.google.protobuf.Timestamp;

import java.util.Map;

public class RawMessageState {
    private byte[] body;
    private Map<String, String> properties;
    private String sessionAlias;
    private Direction direction;
    private long seconds;
    private int nanos;
    private String protocolType;

    public RawMessageState() {
    }

    public RawMessageState(byte[] body,
                           Map<String, String> properties,
                           String sessionAlias,
                           Direction direction,
                           Timestamp timestamp,
                           String protocolType) {
        this.body = body;
        this.properties = properties;
        this.sessionAlias = sessionAlias;
        this.direction = direction;
        this.seconds = timestamp.getSeconds();
        this.nanos = timestamp.getNanos();
        this.protocolType = protocolType;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public String getSessionAlias() {
        return sessionAlias;
    }

    public void setSessionAlias(String sessionAlias) {
        this.sessionAlias = sessionAlias;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public long getSeconds() {
        return seconds;
    }

    public void setSeconds(long seconds) {
        this.seconds = seconds;
    }

    public int getNanos() {
        return nanos;
    }

    public void setNanos(int nanos) {
        this.nanos = nanos;
    }

    public String getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }
}
