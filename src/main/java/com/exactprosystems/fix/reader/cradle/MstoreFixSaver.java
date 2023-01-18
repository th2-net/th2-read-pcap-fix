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

import com.exactpro.th2.common.grpc.Direction;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.RawMessage;
import com.exactpro.th2.common.grpc.RawMessageBatch;
import com.exactpro.th2.common.grpc.RawMessageMetadata;
import com.exactpro.th2.common.schema.message.MessageRouter;
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

public class MstoreFixSaver extends AbstractMstoreSaver implements FixListener {
    private static final String PACKET_TYPE = "FIX_PACKET";
    private static final Logger log = LoggerFactory.getLogger(MstoreFixSaver.class);

    public MstoreFixSaver(MessageRouter<RawMessageBatch> messageRouter,
                          MessageRouter<EventBatch> eventRouter,
                          PcapFileReaderConfiguration configuration,
                          TcpStreamFactory tcpStreamFactory) {
        super(messageRouter, eventRouter, configuration, tcpStreamFactory);
    }

    @Override
    public long onMessagesRead(boolean isClientFrame, FixMessage msg, Pcap associatedPacket, boolean saveToDb) throws IOException {
        if (!saveToDb) {
            return 0;
        }
        long messageCount = 0;
        try {
            if (configuration.isSortTh2Messages()) {
                RawMessage.Builder builder = prepareMessageBuilder(msg, isClientFrame);
                messageSorter.onMessagesRead(builder);
            } else {
                RawMessage rawMessage = prepareMessage(msg, isClientFrame);
                if (rawMessage.getMetadata().getId().getDirection() == Direction.FIRST) {
                    firstBatcher.onMessage(rawMessage);
                } else {
                    secondBatcher.onMessage(rawMessage);
                }
            }
            messageCount++;
        } catch (Exception e) {
            log.error("Unable to store fix messages in mstore", e);
        }
        return messageCount;
    }

    private RawMessage prepareMessage(FixMessage message, boolean isClientFrame) {
        Map<String, String> properties = new HashMap<>();

        properties.put(PACKET_TYPE, "FIX_type"); //todo
        properties.put(PACKET_NUMBER_PROPERTY, String.valueOf(message.getAssociatedPacketNumber()));
        properties.put(FILE_NAME_PROPERTY, message.getAssociatedPacketFileName());
        properties.put(SOURCE_IP_PROPERTY, message.getSourceIP());
        properties.put(DESTINATION_IP_PROPERTY, message.getDestinationIP());
        properties.put(SOURCE_PORT_PROPERTY, String.valueOf(message.getSourcePort()));
        properties.put(DESTINATION_PORT_PROPERTY, String.valueOf(message.getDestinationPort()));

        RawMessageMetadata md = createMetadata(isClientFrame ? Direction.SECOND : Direction.FIRST,
                properties, message.getAssociatedPacketTimestamp(), ProtocolType.FIX, configuration,
                getNextSeqNum(message.getStreamName(), isClientFrame ? Direction.SECOND : Direction.FIRST), message.getStreamName());

        RawMessage rawMessage = RawMessage.newBuilder()
                .setMetadata(md)
                .setBody(ByteString.copyFrom(message.getData().getBytes(StandardCharsets.UTF_8)))
                .build();

        return rawMessage;
    }

    private RawMessage.Builder prepareMessageBuilder(FixMessage message, boolean isClientFrame) {
        Map<String, String> properties = new HashMap<>();

        properties.put(PACKET_TYPE, "FIX_type"); //todo
        properties.put(PACKET_NUMBER_PROPERTY, String.valueOf(message.getAssociatedPacketNumber()));
        properties.put(FILE_NAME_PROPERTY, message.getAssociatedPacketFileName());
        properties.put(SOURCE_IP_PROPERTY, message.getSourceIP());
        properties.put(DESTINATION_IP_PROPERTY, message.getDestinationIP());
        properties.put(SOURCE_PORT_PROPERTY, String.valueOf(message.getSourcePort()));
        properties.put(DESTINATION_PORT_PROPERTY, String.valueOf(message.getDestinationPort()));

        RawMessageMetadata.Builder metadataBuilder = createMetadataBuilder(isClientFrame ? Direction.SECOND : Direction.FIRST,
                message.getStreamName(),
                properties,
                message.getAssociatedPacketTimestamp(),
                ProtocolType.FIX,
                configuration);
        return RawMessage.newBuilder()
                .setMetadata(metadataBuilder)
                .setBody(ByteString.copyFromUtf8(message.getData()));
    }
}
