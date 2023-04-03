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

package com.exactrpo.th2.pcapreader.source.impl;

import java.time.Instant;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.exactrpo.th2.pcapreader.common.AbstractPcapTest;
import com.exactrpo.th2.pcapreader.packet.PcapPacket;

class TestFilePacketSource extends AbstractPcapTest {

    @Test
    void emptyFile() throws Exception {
        openPcap(BASE_DATA, "empty.pcap", source -> {
            Assertions.assertFalse(source.hasNextPacket(), "empty file does not have any packets");
            Assertions.assertFalse(source.hasAvailableData(), "should be any data in empty file");
        });
    }

    @Test
    void emptyOnlyGlobalHeader() throws Exception {
        openPcap(BASE_DATA, "global_header.pcap", source -> {
            Assertions.assertFalse(source.hasNextPacket(), "only global header file does not have any packets");
            Assertions.assertFalse(source.hasAvailableData(), "global header should be read");
        });
    }

    @Test
    void notFullPcap() throws Exception {
        openPcap(BASE_DATA, "not_full.pcap", source -> {
            Assertions.assertFalse(source.hasNextPacket(), "only part of the packet is written in file");
            Assertions.assertTrue(source.hasAvailableData(), "the part of the packet should not be read");
        });
    }

    @Test
    void singlePacket() throws Exception {
        openPcap(BASE_DATA, "single.pcap", source -> {
            Assertions.assertTrue(source.hasNextPacket(), "only part of the packet is written in file");

            PcapPacket packet = source.nextPacket();
            Assertions.assertAll(
                    () -> Assertions.assertEquals(1, packet.getPacketNumber(), "unexpected packet number"),
                    () -> Assertions.assertEquals(74, packet.getCapturedLength(), "unexpected capture length"),
                    () -> Assertions.assertEquals(74, packet.getOriginalLength(), "unexpected original length"),
                    () -> Assertions.assertEquals(
                            Instant.ofEpochSecond(1676973457, 251823000),
                            packet.getArrivalTimestamp(),
                            "unexpected arrival time"
                    )
            );

            Assertions.assertFalse(source.hasNextPacket(), "source should not have any packets left");
            Assertions.assertFalse(source.hasAvailableData(), "nothing is left in file");
        });
    }
}