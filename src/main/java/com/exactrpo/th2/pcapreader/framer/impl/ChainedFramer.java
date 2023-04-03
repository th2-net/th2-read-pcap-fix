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

package com.exactrpo.th2.pcapreader.framer.impl;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactrpo.th2.pcapreader.framer.PacketFramer;
import com.exactrpo.th2.pcapreader.packet.Packet;

public class ChainedFramer<IN extends Packet, INTER extends Packet, OUT extends Packet>
        implements PacketFramer<IN, OUT> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChainedFramer.class);
    private final Collection<PacketFramer<INTER, OUT>> chained;

    private final PacketFramer<IN, INTER> main;

    public ChainedFramer(
            PacketFramer<IN, INTER> main,
            Collection<PacketFramer<INTER, OUT>> chained
    ) {
        this.chained = Objects.requireNonNull(chained, "chained framers");
        if (chained.isEmpty()) {
            throw new IllegalArgumentException("at least one framer must be changed");
        }
        this.main = Objects.requireNonNull(main, "main framer");
    }

    @Override
    public boolean accept(IN parent) {
        return main.accept(parent);
    }

    @Nullable
    @Override
    public OUT frame(IN parent) {
        INTER frame = main.frame(parent);
        LOGGER.trace("Framer {} produced {} from {}", main, frame, parent);
        for (var chain : chained) {
            if (chain.accept(frame)) {
                LOGGER.trace("Framer {} accepted frame {}",
                        chain instanceof ChainedFramer ? ((ChainedFramer<?, ?, ?>)chain).getActual() : chain,
                        frame);
                return chain.frame(frame);
            }
        }
        return null;
    }

    private PacketFramer<?, ?> getActual() {
        if (main instanceof ChainedFramer) {
            return ((ChainedFramer<?, ?, ?>)main).getActual();
        }
        return main;
    }

    public static <IN extends Packet, INTER extends Packet, OUT extends Packet> PacketFramer<IN, OUT> chainFramers(
            PacketFramer<IN, INTER> main,
            Collection<PacketFramer<INTER, OUT>> chained
    ) {
        return new ChainedFramer<>(main, chained);
    }

    @SafeVarargs
    public static <IN extends Packet, INTER extends Packet, OUT extends Packet> PacketFramer<IN, OUT> chainFramers(
            PacketFramer<IN, INTER> main,
            PacketFramer<INTER, OUT>... chained
    ) {
        return chainFramers(main, List.of(chained));
    }
}
