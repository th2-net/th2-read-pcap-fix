/*
 * Copyright 2022-2023 Exactpro (Exactpro Systems Limited)
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

package com.exactprosystems.fix.reader.pcapreader;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class RecordReader implements AutoCloseable {
    private InputStream is;
    private long tcpdumpSnapshotLength;

    public RecordReader(InputStream inputStream, long tcpdumpSnapshotLength) {
        is = Objects.requireNonNull(inputStream, "inputStream cannot be null");
        this.tcpdumpSnapshotLength = tcpdumpSnapshotLength;
    }

    @Override
    public void close() throws IOException {
        if (is != null) {
            is.close();
            is = null;
        }
    }

    /**
     * Try to fill the supplied buffer fully
     *
     * <p> This method blocks until input data is available, end of file is
     * detected, or an exception is thrown.
     *
     * @param ba the buffer to fill
     * @return <code>false</code> if the InputStream is at EOF, <code>true</code> if buffer was filled completely
     * @throws IOException when there's less then ba.length bytes in the <code>InputStream</code>
     */
    public boolean fill(ByteBuf buf, int length) throws IOException {
        return fill(buf, 0, length);
    }

    /**
     * Try to fill the supplied buffer range fully.
     *
     * <p> This method blocks until input data is available, end of file is
     * detected, or an exception is thrown.
     *
     * @param ba  the buffer to fill.
     * @param off offset
     * @param len number of bytes to fill
     * @return <code>false</code> if the <code>InputStream</code> is at EOF, <code>true</code> if buffer range was filled completely
     * @throws IOException when there's less then <code>len</code> bytes in the <code>InputStream</code>
     */
    public boolean fill(ByteBuf buf, int off, int len) throws IOException {
        if (is == null) {
            throw new IllegalStateException("InputStream is already closed.");
        }
        if (off < 0 || len < 0) {
            throw new IndexOutOfBoundsException(String.format("Offset %d, len %d make no sense for an array of %d bytes", off, len, len));
        }

        if (len > tcpdumpSnapshotLength) {
            throw new IllegalArgumentException(String.format("Packet length %d could not be greater than tcpdump snapshot length %d", len, tcpdumpSnapshotLength));
        }

        int readableBytes = buf.readableBytes();

        int written = buf.writeBytes(is, len);
        while (buf.readableBytes() - readableBytes != len && written > 0) {
            written = buf.writeBytes(is, len - (buf.readableBytes() - readableBytes));
        }
        return buf.readableBytes() - readableBytes == len;
    }

    public void skip(int len) throws IOException {
        if (is == null) {
            throw new IllegalStateException("InputStream is already closed.");
        }
        if (len < 0) {
            throw new IndexOutOfBoundsException(String.format("Len %d make no sense", len));
        }

        if (len > tcpdumpSnapshotLength) {
            throw new IllegalArgumentException(String.format("Packet length %d could not be greater than tcpdump snapshot length %d", len, tcpdumpSnapshotLength));
        }

        long skipped;
        long curSkipped = skipped = is.skip(len);
        while (skipped < len && curSkipped > 0) {
            curSkipped = is.skip(len - skipped);
            skipped += curSkipped;
        }
    }
}
