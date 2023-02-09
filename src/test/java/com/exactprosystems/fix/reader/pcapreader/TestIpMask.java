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
package com.exactprosystems.fix.reader.pcapreader;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TestIpMask {
    @ParameterizedTest
    @ValueSource(strings = {
            "*.*.*.*",
            "192.*.*.*",
            "192.168.*.*",
            "192.168.0.*",
            "192.168.0.1",
    })
    void acceptsByMask(String mask) {
        IpMask ipMask = IpMask.createFromString(mask);
        String ipAddress = "192.168.0.1";
        int ip = ParsingUtils.ipToInt(ipAddress);
        assertTrue(ipMask.matches(ip), () -> ipAddress + " does not match mask " + mask);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*.*.*",
            "*.**.*.*",
            "192.*.0.1",
            "192.168.0.**",
    })
    void reportIncorrectMask(String mask) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> IpMask.createFromString(mask));
        System.out.println(exception.getMessage());
    }
}