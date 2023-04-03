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

package com.exactrpo.th2.pcapreader.framer.impl;

import static com.exactrpo.th2.pcapreader.packet.impl.UdpPacketImpl.HEADER_LENGTH;
import static com.exactrpo.th2.pcapreader.packet.impl.UdpPacketImpl.LENGTH_OFFSET;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.exactrpo.th2.pcapreader.framer.PacketFramer;
import com.exactrpo.th2.pcapreader.packet.IpPacket;
import com.exactrpo.th2.pcapreader.packet.UdpPacket;
import com.exactrpo.th2.pcapreader.packet.impl.UdpPacketImpl;

import io.netty.buffer.ByteBuf;

public class UdpFramer implements PacketFramer<IpPacket, UdpPacket> {
    private static final byte PROTOCOL = 0x11;

    @Override
    public boolean accept(IpPacket parent) {
        return parent.getProtocol() == PROTOCOL;
    }

    @Nullable
    @Override
    public UdpPacket frame(IpPacket parent) {
        Objects.requireNonNull(parent, "parent");
        ByteBuf payload = Objects.requireNonNull(parent.getPayload(), "parent payload");
        int totalLength = payload.getUnsignedShort(LENGTH_OFFSET);
        ByteBuf header = payload.slice(0, HEADER_LENGTH);
        ByteBuf data = payload.slice(HEADER_LENGTH, totalLength - HEADER_LENGTH);
        return new UdpPacketImpl(parent, header, data);
    }
}
