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

import com.exactprosystems.fix.reader.pcapreader.constants.PcapTimestampResolution;

import java.util.Arrays;

public final class MagicNumbers {
    public static PcapTimestampResolution identifyPcapTimestampResolution(byte[] magicNumber) {
        if (magicNumberEquals(new byte[]{(byte) 0xA1, (byte) 0xB2, (byte) 0xC3, (byte) 0xD4}, magicNumber)
                || magicNumberEquals(new byte[]{(byte) 0xD4, (byte) 0xC3, (byte) 0xB2, (byte) 0xA1}, magicNumber)) {
            return PcapTimestampResolution.MICROSECOND_RESOLUTION;
        }
        if (magicNumberEquals(new byte[]{(byte) 0xA1, (byte) 0xB2, (byte) 0x3C, (byte) 0x4D}, magicNumber)
                || magicNumberEquals(new byte[]{(byte) 0x4D, (byte) 0x3C, (byte) 0xB2, (byte) 0xA1}, magicNumber)) {
            return PcapTimestampResolution.NANOSECOND_RESOLUTION;
        }
        return PcapTimestampResolution.UNDEFINED;
    }

    private static boolean magicNumberEquals(byte[] expected, byte[] actualHeader) {
        return Arrays.equals(expected, 0, 4, actualHeader, 0, 4);
    }
}
