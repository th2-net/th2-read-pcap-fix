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

import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactrpo.th2.pcapreader.common.AbstractPcapTest;
import com.exactrpo.th2.pcapreader.framer.PacketFramer;
import com.exactrpo.th2.pcapreader.packet.IpPacket;
import com.exactrpo.th2.pcapreader.packet.MacPacket;
import com.exactrpo.th2.pcapreader.packet.PcapPacket;

class TestIpV4Framer extends AbstractPcapTest {
    static final PacketFramer<MacPacket, IpPacket> FRAMER = new IpV4Framer();

    private static final PacketFramer<PcapPacket, IpPacket> CHAINED = ChainedFramer.chainFramers(
            TestEthernetFramer.FRAMER,
            FRAMER
    );

    @Test
    void extractsIpPacket() throws Exception {
        openPcap(BASE_DATA, "single.pcap", source -> {
            PcapPacket packet = source.nextPacket();
            assertTrue(CHAINED.accept(packet), "should accept packet");
            IpPacket ipPacket = CHAINED.frame(packet);
            assertNotNull(ipPacket, "ip packet must be produced");
            int localHost = ParsingUtils.ipToInt("127.0.0.1");
            assertAll(
                    () -> assertNotNull(ipPacket.getParent(), "must have a parent"),
                    () -> assertEquals(localHost, ipPacket.getSourceIp(), "unexpected src IP"),
                    () -> assertEquals(localHost, ipPacket.getDestinationIp(), "unexpected dst IP"),
                    () -> assertEquals(6, ipPacket.getProtocol(), "unexpected protocol")
            );
        });
    }
}