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

import org.jetbrains.annotations.NotNull;

import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactrpo.th2.pcapreader.packet.MacPacket;
import com.exactrpo.th2.pcapreader.packet.PcapPacket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

public class EthernetPacket extends AbstractPacket<PcapPacket> implements MacPacket {
    public static final int HEADER_SIZE = 14;

    private static final int MAC_SIZE = 6;
    private final ByteBuf header;
    private final String sourceMac;
    private final String destMac;

    public EthernetPacket(PcapPacket parent, ByteBuf header, ByteBuf payload) {
        super(parent, payload);
        this.header = Objects.requireNonNull(header, "header");
        sourceMac = formatMac(header, 0);
        destMac = formatMac(header, MAC_SIZE);
    }

    @Override
    public String getSourceMac() {
        return sourceMac;
    }

    @Override
    public String getDestinationMac() {
        return destMac;
    }

    @Override
    public int getProtocolType() {
        return header.getUnsignedShort(MAC_SIZE * 2);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", EthernetPacket.class.getSimpleName() + "[", "]")
                .add("parent=" + getParent())
                .add("sourceMac='" + sourceMac + "'")
                .add("destMac='" + destMac + "'")
                .add("protocol=" + protocolHex())
                .toString();
    }

    private static String formatMac(ByteBuf header, int offset) {
        var buffer = new StringBuilder();
        for (int i = offset; i < offset + MAC_SIZE; i++) {
            buffer.append(ParsingUtils.byteToHex(header.getByte(i)));
            buffer.append(':');
        }
        return buffer.substring(0, buffer.length() - 1);
    }

    @NotNull
    private String protocolHex() {
        return ByteBufUtil.hexDump(header, MAC_SIZE * 2, 2);
    }

    @Override
    protected void toPrettyString(StringWriter writer) {
        writer.write("Src MAC: ");
        writer.write(sourceMac);
        writer.write("; Dst MAC: ");
        writer.write(destMac);
        newLine(writer);
        writer.write("Sub Protocol: 0x");
        writer.write(protocolHex());
        newLine(writer);
    }
}
