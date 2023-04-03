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

import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactrpo.th2.pcapreader.packet.IpPacket;
import com.exactrpo.th2.pcapreader.packet.MacPacket;

import io.netty.buffer.ByteBuf;

public class IpV4Packet extends AbstractPacket<MacPacket> implements IpPacket {
    private static final int SOURCE_IP_OFFSET = 12;
    private static final int DEST_IP_OFFSET = 16;
    private static final int PROTOCOL_OFFSET = 9;
    private final ByteBuf header;

    public IpV4Packet(MacPacket parent, ByteBuf header, ByteBuf payload) {
        super(Objects.requireNonNull(parent, "parent"), payload);
        this.header = Objects.requireNonNull(header, "header");
    }

    @Override
    public int getSourceIp() {
        return header.getInt(SOURCE_IP_OFFSET);
    }

    @Override
    public int getDestinationIp() {
        return header.getInt(DEST_IP_OFFSET);
    }

    @Override
    public byte getProtocol() {
        return header.getByte(PROTOCOL_OFFSET);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", IpV4Packet.class.getSimpleName() + "[", "]")
                .add("parent=" + getParent())
                .add("sourceIp=" + getSourceIp())
                .add("destinationIp=" + getDestinationIp())
                .add("protocol=" + getProtocol())
                .toString();
    }

    @Override
    protected void toPrettyString(StringWriter writer) {
        writer.write("Src IP: ");
        writer.write(ParsingUtils.intToIp(getSourceIp()));
        writer.write("; Dst IP: ");
        writer.write(ParsingUtils.intToIp(getDestinationIp()));
        newLine(writer);
        writer.write("Protocol: ");
        writer.write(String.valueOf(getProtocol()));
        newLine(writer);
    }
}
