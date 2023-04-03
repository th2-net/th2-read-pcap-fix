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

package com.exactrpo.th2.pcapreader.source.cfg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TestDataSize {
    @ParameterizedTest
    @MethodSource("values")
    void parses(String value, int expectedBytes) {
        assertEquals(
                expectedBytes,
                DataSize.parse(value).getBytes()
        );
    }

    static List<Arguments> values() {
        return List.of(
                arguments("1B", 1),
                arguments("1KB", 1024),
                arguments("1MB", (int)Math.pow(1024.0, 2)),
                arguments("1GB", (int)Math.pow(1024.0, 3)),
                arguments("156KB", 156 * 1024)
        );
    }
}