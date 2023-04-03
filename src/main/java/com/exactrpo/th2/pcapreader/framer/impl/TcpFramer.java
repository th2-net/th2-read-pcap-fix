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
import com.exactrpo.th2.pcapreader.packet.TcpPacket;
import com.exactrpo.th2.pcapreader.packet.impl.TcpPacketImpl;

import io.netty.buffer.ByteBuf;

public class TcpFramer implements PacketFramer<IpPacket, TcpPacket> {
    private static final int BITS_IN_WORD = 32;
    private static final byte PROTOCOL = 6;
    private static final int DATA_OFFSET_INDEX = 12;
    private static final int LOWER_4_BITS_MASK = 0x0f;

    @Override
    public boolean accept(IpPacket parent) {
        return parent.getProtocol() == PROTOCOL;
    }

    @Nullable
    @Override
    public TcpPacket frame(IpPacket parent) {
        Objects.requireNonNull(parent, "parent");
        ByteBuf payload = Objects.requireNonNull(parent.getPayload(), "parent payload");
        int dataOffsetWords = (payload.getByte(DATA_OFFSET_INDEX) >>> 4) & LOWER_4_BITS_MASK;
        int dataOffset = BITS_IN_WORD * dataOffsetWords / 8;
        ByteBuf header = payload.slice(0, dataOffset);
        ByteBuf data = payload.readableBytes() > dataOffset
                ? payload.slice(dataOffset, payload.readableBytes() - dataOffset)
                : EMPTY;
        return new TcpPacketImpl(parent, header, data);
    }
}
