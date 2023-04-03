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

package com.exactrpo.th2.pcapreader.util;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;

public class InputStreamUtils {
    private InputStreamUtils() {
    }

    @Nullable
    public static byte[] readBytes(InputStream in, int size) throws IOException {
        if (in.available() < size) {
            return null;
        }
        byte[] data = new byte[size];
        int read = in.read(data);
        if (read != size) {
            throw new IllegalStateException("read " + read + " bytes but wanted " + size);
        }
        return data;
    }
}
