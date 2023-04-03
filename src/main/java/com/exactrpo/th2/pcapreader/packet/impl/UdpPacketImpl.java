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

package com.exactrpo.th2.pcapreader.packet.impl;

import java.io.StringWriter;
import java.util.Objects;
import java.util.StringJoiner;

import com.exactrpo.th2.pcapreader.packet.IpPacket;
import com.exactrpo.th2.pcapreader.packet.UdpPacket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

public class UdpPacketImpl extends AbstractPacket<IpPacket> implements UdpPacket {
    public static final int HEADER_LENGTH = 8;
    public static final int LENGTH_OFFSET = 4;
    private static final int SRC_PORT_OFFSET = 0;
    private static final int DST_PORT_OFFSET = 2;
    private final ByteBuf header;

    public UdpPacketImpl(IpPacket parent, ByteBuf header, ByteBuf payload) {
        super(parent, payload);
        this.header = Objects.requireNonNull(header, "header");
    }

    @Override
    public int getSourcePort() {
        return header.getUnsignedShort(SRC_PORT_OFFSET);
    }

    @Override
    public int getDestinationPort() {
        return header.getUnsignedShort(DST_PORT_OFFSET);
    }

    @Override
    public int getLength() {
        return header.getUnsignedShort(LENGTH_OFFSET);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", UdpPacketImpl.class.getSimpleName() + "[", "]")
                .add("parent=" + getParent())
                .add("sourcePort=" + getSourcePort())
                .add("destinationPort=" + getDestinationPort())
                .add("length=" + getLength())
                .toString();
    }

    @Override
    protected void toPrettyString(StringWriter writer) {
        writer.write("Src port: ");
        writer.write(String.valueOf(getSourcePort()));
        writer.write("; Dst port: ");
        writer.write(String.valueOf(getDestinationPort()));
        newLine(writer);
        writer.write("Length: ");
        writer.write(String.valueOf(getLength()));
        newLine(writer);
        writer.write("Payload:");
        if (getPayload().isReadable()) {
            newLine(writer);
            writer.write(ByteBufUtil.prettyHexDump(getPayload()));
        } else {
            writer.write(" EMPTY");
        }
    }
}
