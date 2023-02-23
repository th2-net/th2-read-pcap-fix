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
import com.exactpro.cradle.messages.MessageToStoreBuilder;
import com.exactpro.cradle.messages.StoredMessage;
import com.exactpro.cradle.messages.StoredMessageBatch;
import com.exactpro.cradle.messages.StoredMessageId;
import com.exactpro.cradle.messages.StoredMessageMetadata;
import com.exactpro.cradle.utils.CradleStorageException;
import com.exactpro.th2.common.grpc.Direction;
import com.exactpro.th2.common.grpc.MessageID;
import com.exactpro.th2.common.grpc.RawMessage;
import com.exactpro.th2.common.grpc.RawMessageMetadata;
import com.exactprosystems.fix.reader.connectionevents.ConnectionEvent;
import com.exactprosystems.fix.reader.connectionevents.TcpConnectionEvent;
import com.exactprosystems.fix.reader.eventsender.FileEventType;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.pcapreader.constants.ProtocolType;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStreamFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.exactpro.th2.common.util.StorageUtils.toCradleDirection;
import static com.exactpro.th2.common.util.StorageUtils.toInstant;
import static org.apache.commons.lang3.builder.ToStringStyle.NO_CLASS_NAME_STYLE;

public abstract class AbstractCradleSaver extends Saver {
    private static final Logger logger = LoggerFactory.getLogger(AbstractCradleSaver.class);

    protected final CradleStorage storage;
    protected final PcapFileReaderConfiguration configuration;

    private Map<Pair<String, com.exactpro.cradle.Direction>, StoredMessageBatch> messageBatchMap;
    private final Map<CompletableFuture<Void>, StoredMessageBatch> asyncStoreFutures = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;

    protected AbstractCradleSaver(CradleStorage storage, PcapFileReaderConfiguration configuration, TcpStreamFactory tcpStreamFactory) {
        super(tcpStreamFactory);
        this.storage = storage;
        this.configuration = configuration;
        this.mapper = new ObjectMapper();
        messageBatchMap = new HashMap<>();
    }

    public static Long initSequenceNumber(CradleStorage storage, String prefix, boolean useOffsetFromCradle) {
        long firstMaxSeqNum = -1;
        long secondMaxSeqNum = -1;
        List<Triple<String, com.exactpro.cradle.Direction, Long>> allStreamsLastMessageIndexes = new ArrayList<>();

        try {
            Collection<String> streams = storage.getStreams();
            for (String stream : streams) {
                long firstLastMessageIndex = storage.getLastMessageIndex(stream, com.exactpro.cradle.Direction.FIRST);
                long secondLastMessageIndex = storage.getLastMessageIndex(stream, com.exactpro.cradle.Direction.SECOND);

                allStreamsLastMessageIndexes.add(Triple.of(stream, com.exactpro.cradle.Direction.FIRST, firstLastMessageIndex));
                allStreamsLastMessageIndexes.add(Triple.of(stream, com.exactpro.cradle.Direction.SECOND, secondLastMessageIndex));

                if (firstMaxSeqNum < firstLastMessageIndex) {
                    firstMaxSeqNum = firstLastMessageIndex;
                }

                if (secondMaxSeqNum < secondLastMessageIndex) {
                    secondMaxSeqNum = secondLastMessageIndex;
                }
            }
        } catch (IOException e) {
            logger.error("Error while initializing sequence number from Cradle");
        }

        if (firstMaxSeqNum == -1 && secondMaxSeqNum == -1) {
            startIndexCounter = 0;
            return 0L;
        } else {
            startIndexCounter = Math.max(firstMaxSeqNum, secondMaxSeqNum) + 1;
        }

        if (!useOffsetFromCradle) {
            return 0L;
        }

        try {
            Instant maxTimeStamp = Instant.MIN;
            Long maxPacketNumber = null;
            for (Triple<String, com.exactpro.cradle.Direction, Long> id : allStreamsLastMessageIndexes) {
                StoredMessage message = storage.getMessage(new StoredMessageId(id.getLeft(), id.getMiddle(), id.getRight()));
                if (message == null) {
                    continue;
                }
                StoredMessageMetadata metadata = message.getMetadata();
                Instant timestamp = message.getTimestamp();
                if (metadata != null) {
                    String fileName = metadata.get(FILE_NAME_PROPERTY);
                    if (fileName != null && fileName.startsWith(prefix) && maxTimeStamp.isBefore(timestamp)) {
                        maxTimeStamp = timestamp;
                        String packetNum = metadata.get(PACKET_NUMBER_PROPERTY);
                        maxPacketNumber = StringUtils.isNotBlank(packetNum) ? Long.parseLong(packetNum) : null;
                    }
                } else {
                    RawMessageMetadata rawMessageMetadata = null;
                    try {
                        RawMessage rawMessage  = RawMessage.parseFrom(message.getContent());
                        rawMessageMetadata = rawMessage.getMetadata();
                        String fileName = rawMessageMetadata.getPropertiesOrDefault(FILE_NAME_PROPERTY, null);
                        if (fileName != null && fileName.startsWith(prefix) && maxTimeStamp.isBefore(timestamp)) {
                            maxTimeStamp = timestamp;
                            String packetNum = rawMessageMetadata.getPropertiesOrDefault(PACKET_NUMBER_PROPERTY, null);
                            maxPacketNumber = StringUtils.isNotBlank(packetNum) ? Long.parseLong(packetNum) : null;
                        }
                    } catch (Exception e) {
                        //it is ok
                        continue;
                    }
                }
            }

            if (maxPacketNumber == null) {
                logger.warn("Unable to restore packet number from Cradle");
                return 0L;
            }

            return maxPacketNumber;
        } catch (IOException e) {
            logger.error("Error while initializing sequence number from Cradle");
        }

        return 0L;
    }

    public StoredMessageBatch getMessageBatch(String streamName, com.exactpro.cradle.Direction direction) {
        return messageBatchMap.computeIfAbsent(Pair.of(streamName, direction), key -> storage.getObjectsFactory().createMessageBatch());
    }

    public void saveConnectionEvent(ConnectionEvent event, Pcap packet, boolean isClientStream, boolean saveToDb) {
        if (configuration.isDisableCradleSaving() || configuration.isDisableConnectivitySaving() || !saveToDb) {
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
            long index = getNextSeqNum(streamName, isClientStream ? Direction.SECOND : Direction.FIRST);

            RawMessageMetadata md = createMetadata(isClientStream ? Direction.SECOND : Direction.FIRST,
                    properties, packet.timestamp, protocolType, configuration, index,
                    streamName);

            String jsonEvent = mapper.writeValueAsString(event);

            RawMessage rawMessage = RawMessage.newBuilder()
                    .setMetadata(md)
                    .setBody(ByteString.copyFromUtf8(jsonEvent))
                    .build();


            MessageToStore messageToStore = toCradleMessage(rawMessage, properties);
            StoredMessageBatch batch = getMessageBatch(messageToStore.getStreamName(), messageToStore.getDirection());

            if (!batch.hasSpace(messageToStore)) {
                saveBatchesWithStreamName(messageToStore.getStreamName());
                batch = getMessageBatch(messageToStore.getStreamName(), messageToStore.getDirection());
            }
            batch.addMessage(messageToStore);
        } catch (CradleStorageException | IOException e) {
            logger.error("Error while saving event to Cradle", e);
            if (tcpStreamFactory.getEventPublisher() != null) {
                tcpStreamFactory.getEventPublisher().publishExceptionEvent(packet.source,
                        FileEventType.ERROR, "Error while saving connectivity event to Cradle");
            }
        }
    }

    public void saveBatchesWithStreamName(String streamName) {
        StoredMessageBatch firstMessageBatch = messageBatchMap.remove(Pair.of(streamName, com.exactpro.cradle.Direction.FIRST));
        StoredMessageBatch secondMessageBatch = messageBatchMap.remove(Pair.of(streamName, com.exactpro.cradle.Direction.SECOND));

        if (firstMessageBatch != null) {
            storeBatchAsync(firstMessageBatch);
        }
        if (secondMessageBatch != null) {
            storeBatchAsync(secondMessageBatch);
        }
    }

    public void saveAllBatchesAndRenew() {
        for (StoredMessageBatch batch : messageBatchMap.values()) {
            storeBatchAsync(batch);
        }
        messageBatchMap.clear();
    }

    public void waitAllFutures() throws InterruptedException {
        while (Thread.currentThread().isAlive()) {
            if (asyncStoreFutures.isEmpty()) {
                return;
            } else {
                Thread.sleep(100);
            }
        }
    }

    protected MessageToStore toCradleMessage(RawMessage protoRawMessage, Map<String, String> properties) {
        RawMessageMetadata metadata = protoRawMessage.getMetadata();
        MessageID messageID = metadata.getId();

        MessageToStoreBuilder builder = new MessageToStoreBuilder()
                .streamName(messageID.getConnectionId().getSessionAlias())
                .content(protoRawMessage.toByteArray())
                .timestamp(toInstant(metadata.getTimestamp()))
                .direction(toCradleDirection(messageID.getDirection()))
                .index(messageID.getSequence());

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            builder.metadata(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    protected void storeBatchAsync(StoredMessageBatch holtBatch) {
        if (holtBatch.getBatchSize() == 0) {
            return;
        }
        CompletableFuture<Void> future = store(holtBatch);
        asyncStoreFutures.put(future, holtBatch);
        future.whenCompleteAsync((value, exception) -> {
            try {
                if (exception == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} - batch stored: {}", getClass().getSimpleName(), formatStoredMessageBatch(holtBatch, true));
                    }
                } else {
                    if (logger.isErrorEnabled()) {
                        logger.error("{} - batch storing is failure: {}", getClass().getSimpleName(), formatStoredMessageBatch(holtBatch, true), exception);
                    }
                    if (tcpStreamFactory.getEventPublisher() != null) {
                        tcpStreamFactory.getEventPublisher().publishExceptionEvent(holtBatch.getFirstMessage().getMetadata().get(FILE_NAME_PROPERTY),
                                FileEventType.ERROR, "Error while saving messages to Cradle");
                    }
                }
            } finally {
                if (asyncStoreFutures.remove(future) == null) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("{} - future related to batch {} already removed", getClass().getSimpleName(), formatStoredMessageBatch(holtBatch, true));
                    }
                }
            }
        });
    }

    private static String formatStoredMessageBatch(StoredMessageBatch storedMessageBatch, boolean full) {
        ToStringBuilder builder = new ToStringBuilder(storedMessageBatch, NO_CLASS_NAME_STYLE)
                .append("stream", storedMessageBatch.getStreamName())
                .append("direction", storedMessageBatch.getId().getDirection())
                .append("batch id", storedMessageBatch.getId().getIndex());
        if (full) {
            builder.append("size", storedMessageBatch.getBatchSize())
                    .append("count", storedMessageBatch.getMessageCount())
                    .append("message sequences", storedMessageBatch.getMessages().stream()
                            .map(StoredMessage::getId)
                            .map(StoredMessageId::getIndex)
                            .map(Objects::toString)
                            .collect(Collectors.joining(",", "[", "]")));
        }
        return builder.toString();
    }

    protected CompletableFuture<Void> store(StoredMessageBatch storedMessageBatch) {
        return storage.storeMessageBatchAsync(storedMessageBatch);
    }

    public static void resetSequenceCounters() {
        startIndexCounter = 0;
    }

}
