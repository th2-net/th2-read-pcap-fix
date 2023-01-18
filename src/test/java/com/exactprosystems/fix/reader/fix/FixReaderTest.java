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

package com.exactprosystems.fix.reader.fix;

import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.fix.FIXReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class FixReaderTest {

    @Test
    public void test() throws IOException {
        String body = new String(Files.readAllBytes(Paths.get("src/test/resources/fix/fix_message.txt")));
        FIXReader reader = new FIXReader(null, new PcapFileReaderConfiguration(), null, false);
        List<String> strings = reader.splitFixMessage(body);
        for (String string : strings) {
            if (!string.startsWith("8=FIX")) {
                throw new IllegalArgumentException();
            }
            String substring = string.substring(string.length() - 7);
            if (!substring.startsWith("10=") || !substring.endsWith("\u0001")) {
                throw new IllegalArgumentException();
            }
        }
    }
}
