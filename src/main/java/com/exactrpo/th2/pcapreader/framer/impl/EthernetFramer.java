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
import com.exactrpo.th2.pcapreader.packet.MacPacket;
import com.exactrpo.th2.pcapreader.packet.PcapPacket;
import com.exactrpo.th2.pcapreader.packet.impl.EthernetPacket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

public class EthernetFramer implements PacketFramer<PcapPacket, MacPacket> {
    @Override
    public boolean accept(PcapPacket parent) {
        return true; // TODO: should be added check for Linux cooked-mode capture
    }

    @Nullable
    @Override
    public MacPacket frame(PcapPacket parent) {
        Objects.requireNonNull(parent, "parent");
        ByteBuf payload = parent.getPayload();
        Objects.requireNonNull(payload, "parent payload");

        if (!payload.isReadable(EthernetPacket.HEADER_SIZE)) {
            throw new IllegalStateException("cannot read ethernet header from "
                    + ByteBufUtil.hexDump(payload));
        }
        ByteBuf ethernetHeader = payload.slice(0, EthernetPacket.HEADER_SIZE);
        return new EthernetPacket(
                parent,
                ethernetHeader,
                payload.slice(EthernetPacket.HEADER_SIZE,
                        payload.readableBytes() - EthernetPacket.HEADER_SIZE)
        );
    }
}
