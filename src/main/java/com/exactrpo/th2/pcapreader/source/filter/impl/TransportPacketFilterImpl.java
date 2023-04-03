/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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

package com.exactrpo.th2.pcapreader.source.filter.impl;

import com.exactprosystems.fix.reader.pcapreader.IpMask;
import com.exactrpo.th2.pcapreader.packet.IpPacket;
import com.exactrpo.th2.pcapreader.packet.TransportPacket;
import com.exactrpo.th2.pcapreader.source.filter.TransportPacketFilter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TransportPacketFilterImpl implements TransportPacketFilter {
    private final IpMask mask;
    private final Integer port;

    private TransportPacketFilterImpl(IpMask mask, Integer port) {
        this.mask = mask;
        this.port = port;
    }

    @JsonCreator
    public static TransportPacketFilterImpl create(
            @JsonProperty("mask") IpMask mask,
            @JsonProperty("port") Integer port
    ) {
        return new TransportPacketFilterImpl(mask, port);
    }

    @Override
    public boolean accept(TransportPacket packet) {
        return (mask == null || matches(mask, packet.getParent()))
                && (port == null || matches(port, packet));
    }

    private boolean matches(int port, TransportPacket packet) {
        return port == packet.getSourcePort() || port == packet.getDestinationPort();
    }
    private boolean matches(IpMask mask, IpPacket packet) {
        return mask.matches(packet.getDestinationIp()) || mask.matches(packet.getSourceIp());
    }
}
