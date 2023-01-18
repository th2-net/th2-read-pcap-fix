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

import com.exactprosystems.fix.reader.eventsender.EventPublisher;
import com.exactprosystems.fix.reader.pcapreader.constants.ProtocolType;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public abstract class FileReader implements AutoCloseable {
    protected String fileType;

    protected FileReader(String fileType) {
        this.fileType = fileType;
    }

    public abstract ParseResult readAndParseAndStoreToDb(PacketSource source,
                                                         long checkpoint,
                                                         long offset,
                                                         Long offsetFromDb,
                                                         EventPublisher eventPublisher,
                                                         Instant previousFileLastPacketTimestamp,
                                                         Long stopAfter,
                                                         String stopFile);

    @Override
    public void close() throws Exception {
    }

    public final static class ParseResult {
        public static final ParseResult ZERO = new ParseResult(Collections.EMPTY_LIST);

        private final List<Pair<ProtocolType, Long>> savedMessagesCount;
        private final List<ProtocolType> protocols;
        private final long bytesRead;
        private final long packetsRead;
        private final boolean success;
        private final Instant firstPacketTimestamp;
        private final Instant lastPacketTimestamp;

        public ParseResult(List<Pair<ProtocolType, Long>> savedMessagesCount) {
            this(savedMessagesCount, Collections.EMPTY_LIST, 0L, 0L, false, null, null);
        }

        public ParseResult(List<Pair<ProtocolType, Long>> savedMessagesCount,
                           List<ProtocolType>protocols,
                           long bytesRead,
                           long packetsRead,
                           boolean success,
                           Instant firstPacketTimestamp,
                           Instant lastPacketTimestamp) {
            this.savedMessagesCount = savedMessagesCount;
            this.protocols = protocols;
            this.bytesRead = bytesRead;
            this.packetsRead = packetsRead;
            this.success = success;
            this.firstPacketTimestamp = firstPacketTimestamp;
            this.lastPacketTimestamp = lastPacketTimestamp;
        }

        public long getBytesRead() {
            return bytesRead;
        }

        public long getPacketsRead() {
            return packetsRead;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<ProtocolType> getProtocols() {
            return protocols;
        }

        public Instant getFirstPacketTimestamp() {
            return firstPacketTimestamp;
        }

        public Instant getLastPacketTimestamp() {
            return lastPacketTimestamp;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ParseResult{");
            sb.append("savedMessagesCount=").append(savedMessagesCount);
            sb.append(", protocols=").append(protocols);
            sb.append(", bytesRead=").append(bytesRead);
            sb.append(", packetsRead=").append(packetsRead);
            sb.append(", success=").append(success);
            sb.append('}');
            return sb.toString();
        }
    }
}
