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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.exactrpo.th2.pcapreader.common.AbstractPcapTest;
import com.exactrpo.th2.pcapreader.framer.PacketFramer;
import com.exactrpo.th2.pcapreader.packet.IpPacket;
import com.exactrpo.th2.pcapreader.packet.MacPacket;
import com.exactrpo.th2.pcapreader.packet.PcapPacket;

class TestChainedFramer extends AbstractPcapTest {
    private final PacketFramer<PcapPacket, MacPacket> main = Mockito.mock(PacketFramer.class);
    private final PacketFramer<MacPacket, IpPacket> first = Mockito.mock(PacketFramer.class);
    private final PacketFramer<MacPacket, IpPacket> second = Mockito.mock(PacketFramer.class);
    private final PacketFramer<PcapPacket, IpPacket> framer = ChainedFramer.chainFramers(
            main,
            first,
            second
    );

    private final MacPacket macPacket = Mockito.mock(MacPacket.class);
    private final IpPacket ip1 = Mockito.mock(IpPacket.class);
    private final IpPacket ip2 = Mockito.mock(IpPacket.class);

    private final PcapPacket pcapPacket = Mockito.mock(PcapPacket.class);

    @BeforeEach
    void setUp() {
        Mockito.when(main.frame(ArgumentMatchers.any())).thenReturn(macPacket);
        Mockito.when(first.frame(ArgumentMatchers.any())).thenReturn(ip1);
        Mockito.when(second.frame(ArgumentMatchers.any())).thenReturn(ip2);
    }

    @Test
    void producesFrameIfMainAndFirstChildAccepts() {
        Mockito.when(main.accept(ArgumentMatchers.any())).thenReturn(true);
        Mockito.when(first.accept(ArgumentMatchers.same(macPacket))).thenReturn(true);
        Mockito.when(second.accept(ArgumentMatchers.same(macPacket))).thenReturn(false);

        Assertions.assertTrue(framer.accept(pcapPacket), "should accept packet");
        IpPacket frame = framer.frame(pcapPacket);
        Assertions.assertSame(ip1, frame, () -> "produced unexpected result " + frame);
    }

    @Test
    void producesFrameIfMainAndSecondChildAccepts() {
        Mockito.when(main.accept(ArgumentMatchers.any())).thenReturn(true);
        Mockito.when(first.accept(ArgumentMatchers.same(macPacket))).thenReturn(false);
        Mockito.when(second.accept(ArgumentMatchers.same(macPacket))).thenReturn(true);

        Assertions.assertTrue(framer.accept(pcapPacket), "should accept packet");
        IpPacket frame = framer.frame(pcapPacket);
        Assertions.assertSame(ip2, frame, () -> "produced unexpected result " + frame);
    }

    @Test
    void producesNothingIfMainDoesNotAccept() {
        Mockito.when(main.accept(ArgumentMatchers.any())).thenReturn(false);

        Assertions.assertFalse(framer.accept(pcapPacket), "should not accept packet");
    }

    @Test
    void producesNullIfNobodyMatched() {
        Mockito.when(main.accept(ArgumentMatchers.any())).thenReturn(true);

        Assertions.assertTrue(framer.accept(pcapPacket), "should accept packet");
        IpPacket frame = framer.frame(pcapPacket);
        Assertions.assertNull(frame, () -> "should produce null result but was " + frame);
    }
}