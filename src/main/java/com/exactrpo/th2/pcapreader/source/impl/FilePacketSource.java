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

package com.exactrpo.th2.pcapreader.source.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactprosystems.fix.reader.pcapreader.MagicNumbers;
import com.exactprosystems.fix.reader.pcapreader.constants.PcapTimestampResolution;
import com.exactrpo.th2.pcapreader.packet.PcapPacket;
import com.exactrpo.th2.pcapreader.packet.impl.PcapPacketImpl;
import com.exactrpo.th2.pcapreader.packet.impl.PcapPacketImpl.Header;
import com.exactrpo.th2.pcapreader.source.PcapPacketSource;
import com.exactrpo.th2.pcapreader.util.InputStreamUtils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

public class FilePacketSource implements PcapPacketSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilePacketSource.class);

    private static final int MAX_REMAINING_SIZE = 1024 * 1024; // 1 MB

    private static final OnTruncateFile DUMMY = (packetsRead, remainingData, totalRemaining) ->
            LOGGER.warn("Pcap truncated. Read packets: {}, Total remaining: {}, Remaining data: {}",
                    packetsRead, totalRemaining, ByteBufUtil.hexDump(remainingData));

    static final int GLOBAL_HEADER_SIZE = 24;

    private final BufferedInputStream in;
    private GlobalPacketHeader globalHeader;
    private long packetNumber = 1;
    private final OnTruncateFile onTruncateFile;

    private FilePacketSource(InputStream in, OnTruncateFile onTruncateFile) throws IOException {
        Objects.requireNonNull(in, "input stream");
        this.in = in instanceof BufferedInputStream
                ? (BufferedInputStream)in
                : new BufferedInputStream(in);
        globalHeader = tryParseGlobalHeader(in);
        this.onTruncateFile = Objects.requireNonNull(onTruncateFile, "on truncate file");
    }

    @Override
    public boolean hasAvailableData() throws IOException {
        return in.available() > 0;
    }

    @Override
    public boolean hasNextPacket() throws IOException {
        if (globalHeader == null) {
            globalHeader = tryParseGlobalHeader(in);
        }
        // not enough to read global header
        if (globalHeader == null) {
            return false;
        }
        in.mark(PcapPacket.HEADER_SIZE);
        try {
            byte[] headerBytes = InputStreamUtils.readBytes(in, PcapPacket.HEADER_SIZE);
            // not enough to read record
            if (headerBytes == null) {
                return false;
            }
            Header header = createHeader(headerBytes);
            return in.available() >= header.getCapturedLength();
        } finally {
            in.reset();
        }
    }

    @Override
    public PcapPacket nextPacket() throws IOException {
        if (globalHeader == null) {
            throw new IllegalStateException("cannot read packet before the global header is read");
        }
        byte[] headerBytes = InputStreamUtils.readBytes(in, PcapPacket.HEADER_SIZE);
        if (headerBytes == null) {
            throw new IllegalStateException(String.format("cannot read %d bytes pcap header, available %d",
                    PcapPacket.HEADER_SIZE, in.available()));
        }
        Header header = createHeader(headerBytes);
        byte[] packetPayload = InputStreamUtils.readBytes(in, (int)header.getCapturedLength());
        if (packetPayload == null) {
            throw new IllegalStateException(String.format("cannot read %d bytes payload, available %d",
                    header.getCapturedLength(), in.available()));
        }
        return new PcapPacketImpl(header, Unpooled.wrappedBuffer(packetPayload), packetNumber++);
    }

    @Override
    public void close() throws Exception {
        int available = in.available();
        if (available > 0) {
            LOGGER.warn("{} byte(s) left in pcap", available);
            byte[] bytes = new byte[Math.min(available, MAX_REMAINING_SIZE)];
            in.read(bytes);
            ByteBuf remaining = Unpooled.wrappedBuffer(bytes);
            onTruncateFile.accept(packetNumber, remaining, available);
        }
    }

    @NotNull
    private PcapPacketImpl.Header createHeader(byte[] headerBytes) {
        return PcapPacketImpl.header(Unpooled.wrappedBuffer(headerBytes), globalHeader.isNanosecondsInTimestamp());
    }

    public static PcapPacketSource create(InputStream in) throws IOException {
        return new FilePacketSource(in, DUMMY);
    }

    @Nullable
    private static GlobalPacketHeader tryParseGlobalHeader(InputStream in) throws IOException {
        byte[] globalHeader = InputStreamUtils.readBytes(in, GLOBAL_HEADER_SIZE);
        if (globalHeader == null) {
            return null;
        }
        PcapTimestampResolution timestampResolution = MagicNumbers.identifyPcapTimestampResolution(globalHeader);
        if (timestampResolution == PcapTimestampResolution.UNDEFINED) {
            throw new IllegalStateException("file has unexpected magic number in header: " + Hex.encodeHexString(globalHeader));
        }
        return new GlobalPacketHeader(timestampResolution == PcapTimestampResolution.NANOSECOND_RESOLUTION);
    }

    public interface OnTruncateFile {
        void accept(long packetsRead, ByteBuf remainingData, int totalRemaining);
    }

    private static class GlobalPacketHeader {
        private final boolean nanosecondsInTimestamp;

        private GlobalPacketHeader(boolean nanosecondsInTimestamp) {
            this.nanosecondsInTimestamp = nanosecondsInTimestamp;
        }

        public boolean isNanosecondsInTimestamp() {
            return nanosecondsInTimestamp;
        }
    }
}
