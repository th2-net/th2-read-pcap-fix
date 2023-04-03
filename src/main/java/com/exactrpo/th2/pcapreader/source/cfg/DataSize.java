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

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.exactrpo.th2.pcapreader.util.ParameterUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class DataSize {
    private enum Unit {
        BYTE("B"), K_BYTE("KB"), M_BYTE("MB"), G_BYTE("GB");

        private final int size;
        private final String marker;

        Unit(String marker) {
            this.marker = marker;
            size = (int)Math.pow(1024.0, ordinal());
        }

        public int getSize() {
            return size;
        }

        public String getMarker() {
            return marker;
        }

    }
    private final int bytes;
    private final Unit unit;

    private DataSize(int bytes, Unit unit) {
        ParameterUtil.checkPositive(bytes, "bytes");
        this.bytes = bytes;
        this.unit = Objects.requireNonNull(unit, "unit");
    }

    public int getBytes() {
        return bytes;
    }

    @JsonValue
    @Override
    public String toString() {
        return String.format("%d%s",
                bytes / unit.getSize(),
                unit.getMarker()
        );
    }

    @JsonCreator
    public static DataSize parse(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("blank value");
        }
        Unit unit = findUnit(value);
        String unitValue = value.substring(0, value.length() - unit.getMarker().length());
        int unitSize = Integer.parseInt(unitValue);
        return new DataSize(unitSize * unit.getSize(), unit);
    }

    private static Unit findUnit(String value) {
        Unit[] values = Unit.values();
        for (int i = values.length - 1; i >= 0; i--) {
            Unit unit = values[i];
            if (StringUtils.endsWithIgnoreCase(value, unit.getMarker())) {
                return unit;
            }
        }
        throw new IllegalArgumentException("unsupported unit in " + value);
    }
}
