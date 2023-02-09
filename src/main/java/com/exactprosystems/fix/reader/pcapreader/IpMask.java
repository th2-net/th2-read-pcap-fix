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

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

public class IpMask {
    private static final String WILDCARD_NUMBER = "255";
    private static final byte MASK_SHIFT = 8;
    private static final String WILDCARD = "*";
    private static final byte MATCH_ALL = 32;
    private final int expectedIp;
    private final byte maskSize;

    private IpMask(int mask, byte maskSize) {
        if (maskSize < 0) {
            throw new IllegalArgumentException("mask size must not be negative (" + maskSize + ")");
        }
        this.maskSize = maskSize;
        this.expectedIp = mask >> maskSize;
    }

    public static IpMask createFromString(String ipMask) {
        String[] parts = StringUtils.split(ipMask, '.');
        if (parts.length != 4) {
            throw new IllegalArgumentException("incorrect mask " + ipMask + ". Must have 4 parts split by .");
        }
        byte maskSize = 0;
        int length = parts.length;
        for (int i = 0; i < length; i++) {
            if (WILDCARD.equals(parts[i])) {
                maskSize += MASK_SHIFT;
                parts[i] = WILDCARD_NUMBER;
            } else if (maskSize > 0) {
                throw new IllegalArgumentException("incorrect mask " + ipMask
                        + ". Only " + WILDCARD + " should be used after first wildcard " + WILDCARD);
            }
        }
        int mask;
        try {
            mask = ParsingUtils.ipPartsToInt(parts);
        } catch (Exception ex) {
            throw new IllegalArgumentException("incorrect mask " + ipMask
                    + ". Must consist only of positive numbers and " + WILDCARD + " symbol");
        }
        return new IpMask(mask, maskSize);
    }

    public boolean matches(int ipAsInt) {
        if (maskSize == MATCH_ALL) {
            return true;
        }
        int actualIp = ipAsInt >> maskSize;
        return expectedIp == actualIp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IpMask ipMask = (IpMask)o;
        return expectedIp == ipMask.expectedIp && maskSize == ipMask.maskSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(expectedIp, maskSize);
    }
}
