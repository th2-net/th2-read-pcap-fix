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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.StringJoiner;

import org.jetbrains.annotations.NotNull;

import com.exactrpo.th2.pcapreader.packet.IpPacket;
import com.exactrpo.th2.pcapreader.packet.TcpPacket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

public class TcpPacketImpl extends AbstractPacket<IpPacket> implements TcpPacket {
    private static final int SRC_PORT_OFFSET = 0;
    private static final int DST_PORT_OFFSET = 2;
    private static final int SEQ_OFFSET = 4;
    private static final int ACK_OFFSET = 8;
    private static final int WINDOW_OFFSET = 14;
    private static final int FIRST_FLAGS_BYTE_OFFSET = 13;
    private static final int LAST_FLAGS_BYTE_OFFSET = 12;
    private final ByteBuf header;

    public TcpPacketImpl(IpPacket parent, ByteBuf header, ByteBuf payload) {
        super(parent, payload);
        this.header = Objects.requireNonNull(header, "header");
    }

    @Override
    public long getSequenceNumber() {
        return header.getUnsignedInt(SEQ_OFFSET);
    }

    @Override
    public long getAcknowledgementNumber() {
        return header.getUnsignedInt(ACK_OFFSET);
    }

    @Override
    public int getWindowSize() {
        return header.getUnsignedShort(WINDOW_OFFSET);
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
    public boolean isFlagSet(Flag flag) {
        byte flagByte = header.getByte(
                flag.getPosition() > 7
                        ? LAST_FLAGS_BYTE_OFFSET
                        : FIRST_FLAGS_BYTE_OFFSET
        );
        return (flagByte & flag.getMask()) == flag.getMask();
    }

    @Override
    public String toString() {
        Collection<Flag> flags = getFlags();

        return new StringJoiner(", ", TcpPacketImpl.class.getSimpleName() + "[", "]")
                .add("parent=" + getParent())
                .add("sequenceNumber=" + getSequenceNumber())
                .add("acknowledgementNumber=" + getAcknowledgementNumber())
                .add("sourcePort=" + getSourcePort())
                .add("destinationPort=" + getDestinationPort())
                .add("windowSize=" + getWindowSize())
                .add("flags=" + flags)
                .toString();
    }

    @Override
    protected void toPrettyString(StringWriter writer) {
        writer.write("Src port: ");
        writer.write(String.valueOf(getSourcePort()));
        writer.write("; Dst port: ");
        writer.write(String.valueOf(getDestinationPort()));
        newLine(writer);
        writer.write("Seq: ");
        writer.write(String.valueOf(getSequenceNumber()));
        writer.write("; Ack: ");
        writer.write(String.valueOf(getAcknowledgementNumber()));
        newLine(writer);
        writer.write("Flags: ");
        writer.write(String.valueOf(getFlags()));
        newLine(writer);
        writer.write("Payload (");
        writer.write(String.valueOf(getPayload().readableBytes()));
        writer.write(" byte(s)):");
        if (getPayload().isReadable()) {
            newLine(writer);
            writer.write(ByteBufUtil.prettyHexDump(getPayload()));
        } else {
            writer.write(" EMPTY");
        }
    }

    @NotNull
    private Collection<Flag> getFlags() {
        Collection<Flag> flags = new ArrayList<>(2);
        for (Flag flag : Flag.values()) {
            if (isFlagSet(flag)) {
                flags.add(flag);
            }
        }
        return flags;
    }
}
