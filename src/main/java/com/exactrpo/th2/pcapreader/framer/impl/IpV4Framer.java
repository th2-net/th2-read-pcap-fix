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

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.exactrpo.th2.pcapreader.framer.PacketFramer;
import com.exactrpo.th2.pcapreader.packet.IpPacket;
import com.exactrpo.th2.pcapreader.packet.MacPacket;
import com.exactrpo.th2.pcapreader.packet.impl.IpV4Packet;

import io.netty.buffer.ByteBuf;

public class IpV4Framer implements PacketFramer<MacPacket, IpPacket> {

    private static final int MIN_IP_HEADER_LENGTH = 20;
    private static final int IP4_PROTOCOL = 0x0800;
    private static final int INTERNET_HEADER_MASK = 0x0f;
    private static final int IHL_BITS_MULTIPLIER = 32;

    @Override
    public boolean accept(MacPacket parent) {
        return parent.getProtocolType() == IP4_PROTOCOL;
    }

    @Nullable
    @Override
    public IpPacket frame(MacPacket parent) {
        Objects.requireNonNull(parent, "parent");
        ByteBuf payload = parent.getPayload();
        Objects.requireNonNull(payload, "parent payload");
        if (!payload.isReadable(MIN_IP_HEADER_LENGTH)) {
            throw new IllegalStateException(
                    String.format("%d available bytes is less then the min IP V4 header %d",
                            payload.readableBytes(), MIN_IP_HEADER_LENGTH)
            );
        }
        int internetHeaderLength = payload.getByte(0) & INTERNET_HEADER_MASK;
        int length = IHL_BITS_MULTIPLIER * internetHeaderLength / 8;
        int totalLength = payload.getUnsignedShort(2);
        if (!payload.isReadable(totalLength)) {
            throw new IllegalStateException(
                    String.format("not enough to read total IP packet length %d, available %d",
                            totalLength, payload.readableBytes())
            );
        }
        ByteBuf ipHeader = payload.slice(0, length);
        ByteBuf ipPayload = payload.slice(length, totalLength - length);
        return new IpV4Packet(parent, ipHeader, ipPayload);
    }
}
