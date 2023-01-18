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

package com.exactprosystems.fix.reader.cradle;

import com.exactpro.cradle.CradleStorage;
import com.exactpro.cradle.messages.MessageToStore;
import com.exactpro.cradle.messages.StoredMessageBatch;
import com.exactpro.cradle.utils.CradleStorageException;
import com.exactpro.th2.common.grpc.*;
import com.exactprosystems.fix.reader.eventsender.FileEventType;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.fix.FixListener;
import com.exactprosystems.fix.reader.fix.FixMessage;
import com.exactprosystems.fix.reader.pcapreader.constants.ProtocolType;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStreamFactory;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CradleFixSaver extends AbstractCradleSaver implements FixListener {
    private static final String PACKET_TYPE = "FIX_PACKET";
    private static final Logger log = LoggerFactory.getLogger(CradleFixSaver.class);

    public CradleFixSaver(CradleStorage storage, PcapFileReaderConfiguration configuration, TcpStreamFactory tcpStreamFactory) {
       super(storage, configuration, tcpStreamFactory);
    }

    @Override
    public long onMessagesRead(boolean isClientFrame, FixMessage msg, Pcap associatedPacket, boolean saveToDb) throws IOException {
        if (configuration.isDisableCradleSaving() || !saveToDb) {
            return 0;
        }
        long messageCount = 0;
        try {
            MessageToStore messageToStore = prepareMessage(msg, isClientFrame);
            StoredMessageBatch batch = getMessageBatch(messageToStore.getStreamName(), messageToStore.getDirection());
            if (!batch.hasSpace(messageToStore)) {
                saveBatchesWithStreamName(messageToStore.getStreamName());
                batch = getMessageBatch(messageToStore.getStreamName(), messageToStore.getDirection());
            }
            batch.addMessage(messageToStore);
            messageCount++;
        } catch (CradleStorageException e) {
            log.error("Unable to store fix messages in Cradle", e);
            if (tcpStreamFactory.getEventPublisher() != null) {
                tcpStreamFactory.getEventPublisher().publishExceptionEvent(msg.getAssociatedPacketFileName(),
                        FileEventType.ERROR, "Unable to store fix messages in Cradle");
            }
        }
        return messageCount;
    }

    private MessageToStore prepareMessage(FixMessage message, boolean isClientStream) {
        Map<String, String> properties = new HashMap<>();

        properties.put(PACKET_TYPE, "FIX_type"); //todo
        properties.put(PACKET_NUMBER_PROPERTY, String.valueOf(message.getAssociatedPacketNumber()));
        properties.put(FILE_NAME_PROPERTY, message.getAssociatedPacketFileName());

        RawMessageMetadata md = createMetadata(isClientStream ? Direction.SECOND : Direction.FIRST,
                properties, message.getAssociatedPacketTimestamp(), ProtocolType.FIX, configuration,
                getNextSeqNum(message.getStreamName(), isClientStream ? Direction.SECOND : Direction.FIRST), message.getStreamName());

        RawMessage rawMessage = RawMessage.newBuilder()
                .setMetadata(md)
                .setBody(ByteString.copyFrom(message.getData().getBytes(StandardCharsets.UTF_8)))
                .build();

        return toCradleMessage(rawMessage, properties);
    }
}
