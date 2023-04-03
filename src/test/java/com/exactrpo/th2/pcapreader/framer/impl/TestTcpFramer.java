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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.exactrpo.th2.pcapreader.common.AbstractPcapTest;
import com.exactrpo.th2.pcapreader.framer.PacketFramer;
import com.exactrpo.th2.pcapreader.packet.IpPacket;
import com.exactrpo.th2.pcapreader.packet.PcapPacket;
import com.exactrpo.th2.pcapreader.packet.TcpPacket;

class TestTcpFramer extends AbstractPcapTest {
    static final PacketFramer<IpPacket, TcpPacket> FRAMER = new TcpFramer();

    private static final PacketFramer<PcapPacket, TcpPacket> CHAINED = ChainedFramer.chainFramers(
            TestEthernetFramer.FRAMER,
            ChainedFramer.chainFramers(
                    TestIpV4Framer.FRAMER,
                    FRAMER
            )
    );

    @Test
    void extractsTcp() throws Exception {
        openPcap(TCP_DATA, "single_tcp.pcap", source -> {
            PcapPacket packet = source.nextPacket();
            assertTrue(CHAINED.accept(packet), "should accept packet");
            TcpPacket frame = CHAINED.frame(packet);
            assertNotNull(frame, "must not be null");
            assertAll(
                    () -> assertNotNull(frame.getParent(), "must have parent"),
                    () -> assertEquals(36366, frame.getSourcePort(), "unexpected src port"),
                    () -> assertEquals(8080, frame.getDestinationPort(), "unexpected dst port"),
                    () -> assertEquals(4271792308L, frame.getSequenceNumber(), "unexpected seq"),
                    () -> assertEquals(0, frame.getAcknowledgementNumber(), "unexpected ack")
            );
        });
    }
}