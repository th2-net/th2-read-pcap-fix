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

package com.exactrpo.th2.pcapreader.packet;

import javax.annotation.Nonnull;

public interface TcpPacket extends TransportPacket {
    @Nonnull
    @Override
    IpPacket getParent();

    long getSequenceNumber();

    long getAcknowledgementNumber();

    int getWindowSize();

    boolean isFlagSet(Flag flag);

    /**
     * Known TCP flags
     */
    enum Flag {
        FIN(0), SYN(1), RST(2), PSH(3), ACK(4), URG(5), ESE(6), CWR(7),
        NS(8);

        private final int position;
        private final int mask;

        Flag(int position) {
            this.position = position;
            this.mask = 1 << (position % 8);
        }

        public int getPosition() {
            return position;
        }

        public int getMask() {
            return mask;
        }
    }
}
