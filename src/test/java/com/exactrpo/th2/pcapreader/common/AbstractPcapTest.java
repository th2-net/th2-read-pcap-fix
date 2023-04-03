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

package com.exactrpo.th2.pcapreader.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.exactrpo.th2.pcapreader.source.PcapPacketSource;
import com.exactrpo.th2.pcapreader.source.impl.FilePacketSource;

public class AbstractPcapTest {
    private static final Path DATA = Path.of("data", "pcap");
    protected static final Path BASE_DATA = DATA.resolve("base");

    protected static final Path UDP_DAYA = DATA.resolve("udp");

    protected static final Path TCP_DATA = DATA.resolve("tcp");

    private static InputStream openFile(Path dir, String name) throws IOException {
        return Files.newInputStream(dir.resolve(name));
    }

    private static PcapPacketSource openPcap(InputStream in) throws IOException {
        return FilePacketSource.create(in);
    }

    protected static void openPcap(Path dir, String name, ExceptionConsumer<PcapPacketSource> consumer) throws Exception {
        try (InputStream in = openFile(dir, name)) {
            PcapPacketSource source = openPcap(in);
            consumer.accept(source);
        }
    }

    protected interface ExceptionConsumer<T> {
        void accept(T data) throws Exception;
    }
}
