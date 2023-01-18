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

package com.exactprosystems.fix.reader.pcapreader;

import java.util.Objects;

public class PacketTransactSequence {
    private final int payloadSize;
    private final long sequenceNumber;
    private final long acknowledgement;
    private int hash; // Default to 0

    public PacketTransactSequence(int payloadSize, long sequenceNumber, long acknowledgement) {
        this.payloadSize = payloadSize;
        this.sequenceNumber = sequenceNumber;
        this.acknowledgement = acknowledgement;
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public long getAcknowledgement() {
        return acknowledgement;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PacketTransactSequence that = (PacketTransactSequence) o;
        return payloadSize == that.payloadSize && sequenceNumber == that.sequenceNumber && acknowledgement == that.acknowledgement;
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = Objects.hash(payloadSize, sequenceNumber, acknowledgement);
        }
        return hash;
    }
}
