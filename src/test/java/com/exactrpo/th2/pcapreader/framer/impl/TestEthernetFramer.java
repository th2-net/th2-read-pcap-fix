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
import com.exactrpo.th2.pcapreader.packet.MacPacket;
import com.exactrpo.th2.pcapreader.packet.PcapPacket;

class TestEthernetFramer extends AbstractPcapTest {
    static final PacketFramer<PcapPacket, MacPacket> FRAMER = new EthernetFramer();
    @Test
    void extractsMacData() throws Exception {
        openPcap(BASE_DATA, "single.pcap", source -> {
            PcapPacket packet = source.nextPacket();
            assertTrue(FRAMER.accept(packet), "should accept root packet");
            MacPacket macPacket = FRAMER.frame(packet);
            assertNotNull(macPacket, "ethernet packet should not be null");

            assertAll(
                    () -> assertNotNull(macPacket.getParent(), "ethernet should have parent"),
                    () -> assertEquals("00:00:00:00:00:00", macPacket.getSourceMac()),
                    () -> assertEquals("00:00:00:00:00:00", macPacket.getDestinationMac()),
                    () -> assertEquals(0x0800, macPacket.getProtocolType(), "incorrect IP protocol type"),
                    () -> assertTrue(macPacket.getPayload().readableBytes() > 0, "payload must not be empty")
            );
        });
    }
}