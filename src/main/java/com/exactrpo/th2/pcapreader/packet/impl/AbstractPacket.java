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

package com.exactrpo.th2.pcapreader.packet.impl;

import java.io.StringWriter;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.exactrpo.th2.pcapreader.packet.Packet;

import io.netty.buffer.ByteBuf;

public abstract class AbstractPacket<T extends Packet> implements Packet {
    private static final char NEW_LINE = '\n';
    private final T parent;
    private final ByteBuf payload;

    public AbstractPacket(T parent, ByteBuf payload) {
        this.parent = parent;
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    @Nullable
    @Override
    public T getParent() {
        return parent;
    }

    @Override
    public ByteBuf getPayload() {
        return payload;
    }

    @Override
    public String toPrettyString() {
        var writer = new StringWriter();
        appendParent(writer);
        toPrettyString(writer);
        return writer.toString();
    }

    protected abstract void toPrettyString(StringWriter writer);

    private void appendParent(StringWriter writer) {
        if (parent != null) {
            if (parent instanceof AbstractPacket) {
                AbstractPacket<?> abstractPacket = (AbstractPacket<?>)parent;
                abstractPacket.appendParent(writer);
                abstractPacket.toPrettyString(writer);
            } else {
                writer.write(parent.toPrettyString());
            }
        }
    }

    protected static void newLine(StringWriter writer) {
        writer.append(NEW_LINE);
    }
}
