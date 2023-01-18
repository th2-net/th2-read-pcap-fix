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

package com.exactprosystems.fix.reader.pcapreader.starter;

import java.time.Instant;

public class PcapFileEntry {
    private final String filename;
    private final String path;
    private final Instant firstPacketTimestamp;
    private final boolean fromArchive;

    public PcapFileEntry(String filename, String path, Instant firstPacketTimestamp, boolean fromArchive) {
        this.filename = filename;
        this.path = path;
        this.firstPacketTimestamp = firstPacketTimestamp;
        this.fromArchive = fromArchive;
    }

    public String getFilename() {
        return filename;
    }

    public String getPath() {
        return path;
    }

    public Instant getFirstPacketTimestamp() {
        return firstPacketTimestamp;
    }

    public boolean isFromArchive() {
        return fromArchive;
    }
}
