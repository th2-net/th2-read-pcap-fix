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

import java.io.InputStream;
import java.util.Objects;

import static com.exactprosystems.fix.reader.pcapreader.ScheduledFile.DEFAULT_CAPTURE_DAY;

public class PacketSource {
    private final Long sourceId;
    private final String sourceName;
    private final InputStream inputStream;
    private final int captureDay;

    public PacketSource(String sourceName, InputStream inputStream) {
        this(null, sourceName, inputStream, DEFAULT_CAPTURE_DAY);
    }

    public PacketSource(String sourceName, InputStream inputStream, int captureDay) {
        this(null, sourceName, inputStream, captureDay);
    }

    public PacketSource(Long sourceId, String sourceName, InputStream inputStream) {
        this(sourceId, sourceName, inputStream, DEFAULT_CAPTURE_DAY);
    }

    public PacketSource(Long sourceId, String sourceName, InputStream inputStream, int captureDay) {
        this.sourceId = sourceId;
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName cannot be null");
        this.inputStream = Objects.requireNonNull(inputStream, "inputStream cannot be null");
        this.captureDay = captureDay;
    }

    @Override
    public String toString() {
        return String.format("PacketSource[%s, %s]", sourceName, sourceId);
    }


    public Long getSourceId() {
        return sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public int getCaptureDay() {
        return captureDay;
    }
}
