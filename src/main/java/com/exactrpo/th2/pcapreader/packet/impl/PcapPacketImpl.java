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
import java.time.Instant;
import java.util.Objects;
import java.util.StringJoiner;

import com.exactrpo.th2.pcapreader.packet.Packet;
import com.exactrpo.th2.pcapreader.packet.PcapPacket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

public class PcapPacketImpl extends AbstractPacket<Packet> implements PcapPacket {
    public static final int SECONDS_OFFSET = 0;
    public static final int NANO_OR_MICRO_OFFSET = 4;
    private static final int CAPTURED_LENGTH_OFFSET = 8;
    private static final int ORIGINAL_LENGTH_OFFSET = 12;
    private final Header header;
    private final long packetNumber;

    public PcapPacketImpl(Header header, ByteBuf payload, long packetNumber) {
        super(null, payload);
        this.header = Objects.requireNonNull(header, "header");
        if (packetNumber <= 0) {
            throw new IllegalArgumentException("packet number is negative: " + packetNumber);
        }
        this.packetNumber = packetNumber;
    }

    @Override
    public Instant getArrivalTimestamp() {
        return header.getArrival();
    }

    @Override
    public long getCapturedLength() {
        return header.getCapturedLength();
    }

    @Override
    public long getOriginalLength() {
        return header.getOriginalLength();
    }

    @Override
    public long getPacketNumber() {
        return packetNumber;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", PcapPacketImpl.class.getSimpleName() + "[", "]")
                .add("packetNumber=" + packetNumber)
                .add("header=" + header)
                .toString();
    }

    public static Header header(ByteBuf header, boolean nanos) {
        return new Header(header, nanos);
    }

    @Override
    protected void toPrettyString(StringWriter writer) {
        writer.write("Packet #: ");
        writer.write(String.valueOf(packetNumber));
        newLine(writer);
        writer.write("Header: ");
        writer.write(header.toString());
        newLine(writer);
    }

    public static class Header {
        private final ByteBuf header;
        private final Instant arrival;

        public Header(ByteBuf header, boolean useNanos) {
            this.header = Objects.requireNonNull(header, "header");
            long seconds = header.getUnsignedIntLE(SECONDS_OFFSET);
            long microOrNano = header.getUnsignedIntLE(NANO_OR_MICRO_OFFSET);
            if (!useNanos) {
                microOrNano *= 1_000;
            }
            arrival = Instant.ofEpochSecond(seconds, microOrNano);
        }

        public ByteBuf getHeader() {
            return header;
        }

        public Instant getArrival() {
            return arrival;
        }

        public long getCapturedLength() {
            return header.getUnsignedIntLE(CAPTURED_LENGTH_OFFSET);
        }

        public long getOriginalLength() {
            return header.getUnsignedIntLE(ORIGINAL_LENGTH_OFFSET);
        }

        @Override
        public String toString() {
            return ByteBufUtil.hexDump(header);
        }
    }
}
