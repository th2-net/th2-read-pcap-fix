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

import com.exactpro.th2.common.grpc.ConnectionID;
import com.exactpro.th2.common.grpc.Direction;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.MessageID;
import com.exactpro.th2.common.grpc.RawMessage;
import com.exactpro.th2.common.grpc.RawMessageBatch;
import com.exactpro.th2.common.grpc.RawMessageMetadata;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.common.schema.message.QueueAttribute;
import com.exactprosystems.fix.reader.connectionevents.ConnectionEvent;
import com.exactprosystems.fix.reader.connectionevents.TcpConnectionEvent;
import com.exactprosystems.fix.reader.eventsender.FileEventType;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import com.exactprosystems.fix.reader.prometheus.PrometheusMetrics;
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.pcapreader.constants.ProtocolType;
import com.exactprosystems.fix.reader.pcapreader.state.MstoreSaverState;
import com.exactprosystems.fix.reader.pcapreader.state.RawMessageState;
import com.exactprosystems.fix.reader.pcapreader.state.State;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStreamFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public abstract class AbstractMstoreSaver extends Saver {


    private static final Logger logger = LoggerFactory.getLogger(AbstractMstoreSaver.class);
    protected final PcapFileReaderConfiguration configuration;
    protected final MessageRouter<RawMessageBatch> messageRouter;
    protected final MessageRouter<EventBatch> eventRouter;
    protected MessageSorter messageSorter;

    protected final MessageBatcher firstBatcher;
    protected final MessageBatcher secondBatcher;

    private final ObjectMapper mapper;

    protected AbstractMstoreSaver(MessageRouter<RawMessageBatch> messageRouter,
                                  MessageRouter<EventBatch> eventRouter,
                                  PcapFileReaderConfiguration configuration,
                                  TcpStreamFactory tcpStreamFactory) {
        super(tcpStreamFactory);
        this.messageRouter = messageRouter;
        this.eventRouter = eventRouter;
        this.configuration = configuration;
        this.mapper = new ObjectMapper();

        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(configuration.getMessageBatcherCorePoolSize());
        firstBatcher = new MessageBatcher((long) (tcpStreamFactory.getMaxBatchSize()),
                configuration.getMessageBatcherMaxFlushTime(),
                configuration.isCheckMessageBatchSequenceGrowth(),
                configuration.isCheckMessageBatchTimestampGrowth(),
                scheduledExecutorService,
                batch -> {
                    try {
                        if (configuration.isCheckMessageSizeExceedsBatchSize()) {
                            batch.getMessagesList().forEach(msg -> {
                                if (msg.toByteArray().length > tcpStreamFactory.getMaxBatchSize()) {
                                    logger.warn("Message exceeds batch size. {}", msg.getMetadata().getPropertiesMap());
                                }
                            });
                        }
                        RawMessage message = batch.getMessages(batch.getMessagesCount() - 1);
                        PrometheusMetrics.LAST_MESSAGE_TIMESTAMP
                                .labels(message.getMetadata().getId().getConnectionId().getSessionAlias(), message.getMetadata().getId().getDirection().name())
                                .set(Instant.ofEpochSecond(message.getMetadata().getTimestamp().getSeconds(), message.getMetadata().getTimestamp().getNanos()).toEpochMilli());
                        messageRouter.sendAll(batch, QueueAttribute.FIRST.toString());
                    } catch (Exception e) {
                        if (tcpStreamFactory.getEventPublisher() != null) {
                            tcpStreamFactory.getEventPublisher().publishExceptionEvent(batch.getMessages(0).getMetadata().getPropertiesOrDefault(FILE_NAME_PROPERTY, null),
                                    FileEventType.ERROR, "Error while saving messages to Mstore");
                        }
                        if (logger.isErrorEnabled()) {
                            logger.error("Cannot send batch with sequences: {}",
                                    batch.getMessagesList().stream().map(msg -> msg.getMetadata().getId().getSequence()).collect(Collectors.toList()),
                                    e);
                        }
                    }
                });

        secondBatcher = new MessageBatcher((long) (tcpStreamFactory.getMaxBatchSize()),
                configuration.getMessageBatcherMaxFlushTime(),
                configuration.isCheckMessageBatchSequenceGrowth(),
                configuration.isCheckMessageBatchTimestampGrowth(),
                scheduledExecutorService,
                batch -> {
                    try {
                        if (configuration.isCheckMessageSizeExceedsBatchSize()) {
                            batch.getMessagesList().forEach(msg -> {
                                if (msg.toByteArray().length > tcpStreamFactory.getMaxBatchSize()) {
                                    logger.warn("Message exceeds batch size. {}", msg.getMetadata().getPropertiesMap());
                                }
                            });
                        }
                        RawMessage message = batch.getMessages(batch.getMessagesCount() - 1);
                        PrometheusMetrics.LAST_MESSAGE_TIMESTAMP
                                .labels(message.getMetadata().getId().getConnectionId().getSessionAlias(), message.getMetadata().getId().getDirection().name())
                                .set(Instant.ofEpochSecond(message.getMetadata().getTimestamp().getSeconds(), message.getMetadata().getTimestamp().getNanos()).toEpochMilli());
                        messageRouter.sendAll(batch, QueueAttribute.SECOND.toString());
                    } catch (Exception e) {
                        if (tcpStreamFactory.getEventPublisher() != null) {
                            tcpStreamFactory.getEventPublisher().publishExceptionEvent(batch.getMessages(0).getMetadata().getPropertiesOrDefault(FILE_NAME_PROPERTY, null),
                                    FileEventType.ERROR, "Error while saving messages to Mstore");
                        }
                        if (logger.isErrorEnabled()) {
                            logger.error("Cannot send batch with sequences: {}",
                                    batch.getMessagesList().stream().map(msg -> msg.getMetadata().getId().getSequence()).collect(Collectors.toList()),
                                    e);
                        }
                    }
                });

        if (configuration.isSortTh2Messages()) {
            messageSorter = new MessageSorter(this,
                    firstBatcher,
                    secondBatcher,
                    configuration.getMessageSorterWindowSize(),
                    configuration.getMessageSorterConnectionEndTimeout(),
                    configuration.getMessageSorterClearInterval());
        }
    }

    @Override
    public void saveConnectionEvent(ConnectionEvent event, Pcap packet, boolean isClientStream, boolean saveToDb) {

        if (configuration.isDisableConnectivitySaving() || !saveToDb) {
            return;
        }

        ProtocolType protocolType;
        if (event instanceof TcpConnectionEvent) {
            protocolType = ProtocolType.TCP;
        } else {
            protocolType = null;
        }

        try {
            Map<String, String> properties = new HashMap<>();
            properties.put(PACKET_NUMBER_PROPERTY, String.valueOf(packet.packetNum));
            properties.put(FILE_NAME_PROPERTY, packet.source);

            String streamName = getSessionAlias(event, protocolType, isClientStream);

            if (configuration.isSortTh2Messages()) {
                prepareAndSortMessage(streamName, isClientStream, properties, packet, protocolType, event);
            } else {
                prepareAndSendMessage(streamName, isClientStream, properties, packet, protocolType, event);
            }
        } catch (IOException e) {
            logger.error("Error while saving event to Mstore", e);
            if (tcpStreamFactory.getEventPublisher() != null) {
                tcpStreamFactory.getEventPublisher().publishExceptionEvent(packet.source,
                        FileEventType.ERROR, "Error while saving connectivity event to Mstore");
            }
        }
    }

    private void prepareAndSortMessage(String streamName,
                                       boolean isClientStream,
                                       Map<String, String> properties,
                                       Pcap packet,
                                       ProtocolType protocolType,
                                       ConnectionEvent event) throws IOException {
        RawMessageMetadata.Builder metadataBuilder = createMetadataBuilder(isClientStream ? Direction.SECOND : Direction.FIRST,
                streamName, properties, packet.timestamp, protocolType, configuration);
        String jsonEvent = mapper.writeValueAsString(event);
        RawMessage.Builder builder = RawMessage.newBuilder()
                .setMetadata(metadataBuilder)
                .setBody(ByteString.copyFromUtf8(jsonEvent));

        messageSorter.onMessagesRead(builder);
    }

    private void prepareAndSendMessage(String streamName,
                                       boolean isClientStream,
                                       Map<String, String> properties,
                                       Pcap packet,
                                       ProtocolType protocolType,
                                       ConnectionEvent event) throws IOException {
        long index = getNextSeqNum(streamName, isClientStream ? Direction.SECOND : Direction.FIRST);

        RawMessageMetadata md = createMetadata(isClientStream ? Direction.SECOND : Direction.FIRST, properties,
                packet.timestamp, protocolType, configuration, index,
                streamName);

        String jsonEvent = mapper.writeValueAsString(event);

        RawMessage rawMessage = RawMessage.newBuilder()
                .setMetadata(md)
                .setBody(ByteString.copyFromUtf8(jsonEvent))
                .build();

        if (rawMessage.getMetadata().getId().getDirection() == Direction.FIRST) {
            firstBatcher.onMessage(rawMessage);
        } else {
            secondBatcher.onMessage(rawMessage);
        }
    }

    public MstoreSaverState getState() {
        if (messageSorter == null) {
            return null;
        }
        Map<String, List<RawMessageState>> messagesPerSessionAlias = new HashMap<>();
        for (Map.Entry<String, List<RawMessage.Builder>> stringListEntry : messageSorter.getMessagesPerSessionAlias().entrySet()) {
            List<RawMessageState> rawMessageStates = new ArrayList<>();
            for (RawMessage.Builder builder : stringListEntry.getValue()) {
                rawMessageStates.add(new RawMessageState(builder.getBody().toByteArray(),
                        builder.getMetadata().getPropertiesMap(),
                        builder.getMetadata().getId().getConnectionId().getSessionAlias(),
                        builder.getMetadata().getId().getDirection(),
                        builder.getMetadata().getTimestamp(),
                        builder.getMetadata().getProtocol()));
            }
            messagesPerSessionAlias.put(stringListEntry.getKey(), rawMessageStates);
        }
        return new MstoreSaverState(this.getClass(), messagesPerSessionAlias);
    }

    public void loadState(State state) {
        if (messageSorter == null) {
            return;
        }

        if (state.getMstoreSaverStates() == null || state.getMstoreSaverStates().isEmpty()) {
            return;
        }

        MstoreSaverState myState = null;
        for (MstoreSaverState mstoreSaverState : state.getMstoreSaverStates()) {
            if (this.getClass().equals(mstoreSaverState.getClazz())) {
                myState = mstoreSaverState;
                break;
            }
        }

        if (myState == null) {
            return;
        }

        for (Map.Entry<String, List<RawMessageState>> stringListEntry : myState.getMessagesPerSessionAlias().entrySet()) {
            List<RawMessage.Builder> messages = new ArrayList<>();
            for (RawMessageState rawMessageState : stringListEntry.getValue()) {
                ConnectionID connectionID = ConnectionID.newBuilder().setSessionAlias(rawMessageState.getSessionAlias()).build();
                MessageID messageID = MessageID.newBuilder().setConnectionId(connectionID).setDirection(rawMessageState.getDirection()).setSequence(0).build();
                RawMessageMetadata.Builder rawMetadataBuilder = RawMessageMetadata.newBuilder()
                        .setId(messageID)
                        .setProtocol(rawMessageState.getProtocolType())
                        .putAllProperties(rawMessageState.getProperties())
                        .setTimestamp(Timestamp.newBuilder().setSeconds(rawMessageState.getSeconds()).setNanos(rawMessageState.getNanos()).build());
                messages.add(RawMessage.newBuilder().setBody(ByteString.copyFrom(rawMessageState.getBody())).setMetadata(rawMetadataBuilder));
            }
            messageSorter.getMessagesPerSessionAlias().put(stringListEntry.getKey(), messages);
        }
    }

    public void triggerSorter() {
        if (messageSorter != null) {
            messageSorter.processAll();
        }
    }

    public MessageSorter getMessageSorter() {
        return messageSorter;
    }

    public void setMessageSorter(MessageSorter messageSorter) {
        this.messageSorter = messageSorter;
    }
}
