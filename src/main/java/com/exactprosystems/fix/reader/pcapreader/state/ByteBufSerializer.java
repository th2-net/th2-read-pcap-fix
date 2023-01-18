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

package com.exactprosystems.fix.reader.pcapreader.state;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

public class ByteBufSerializer extends StdSerializer<ByteBuf> {
    public ByteBufSerializer(Class<ByteBuf> t) {
        super(t);
    }

    @Override
    public void serialize(ByteBuf value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        byte[] arr = new byte[value.readableBytes()];
        value.getBytes(value.readerIndex(), arr);
        jgen.writeStartObject();
        jgen.writeFieldName("arr");
        jgen.writeBinary(arr);
        jgen.writeEndObject();
    }
}
