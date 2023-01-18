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

import com.exactpro.cradle.messages.MessageToStore;
import com.exactpro.cradle.messages.MessageToStoreBuilder;
import com.exactpro.th2.common.grpc.*;
import com.exactprosystems.fix.reader.prometheus.PrometheusMetrics;
import com.google.protobuf.util.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.exactpro.th2.common.util.StorageUtils.toCradleDirection;
import static com.exactpro.th2.common.util.StorageUtils.toInstant;

public class MessageBatcher implements AutoCloseable {
    private final long maxBatchSize;
    private final long maxFlushTime;
    private final boolean checkMessageBatchSequenceGrowth;
    private final boolean checkMessageBatchTimestampGrowth;
    private final ScheduledExecutorService executorService;
    private final Consumer<RawMessageBatch> onBatch;
    public final static int MESSAGE_SIZE_CONST_VALUE = 30;
    private static final Logger logger = LoggerFactory.getLogger(MessageBatcher.class);


    private final ConcurrentMap<String, MessageBatch> batches;

    public MessageBatcher(long maxBatchSize,
                          long maxFlushTime,
                          boolean checkMessageBatchSequenceGrowth,
                          boolean checkMessageBatchTimestampGrowth,
                          ScheduledExecutorService executorService,
                          Consumer<RawMessageBatch> onBatch) {
        this.maxBatchSize = maxBatchSize;
        this.maxFlushTime = maxFlushTime;
        this.checkMessageBatchSequenceGrowth = checkMessageBatchSequenceGrowth;
        this.checkMessageBatchTimestampGrowth = checkMessageBatchTimestampGrowth;
        this.executorService = executorService;
        this.onBatch = onBatch;

        batches = new ConcurrentHashMap<>();
    }

    public void onMessage(RawMessage message) {
        batches.computeIfAbsent(message.getMetadata().getId().getConnectionId().getSessionAlias(), ignored -> new MessageBatch()).add(message);
    }
    private static void addProperties(MessageToStoreBuilder builder, Map<String, String> properties) {
        properties.forEach(builder::metadata);
    }
    public static MessageToStore toCradleMessage(RawMessage protoRawMessage) {
        RawMessageMetadata metadata = protoRawMessage.getMetadata();
        MessageID messageID = metadata.getId();
        var builder = new MessageToStoreBuilder()
                .streamName(messageID.getConnectionId().getSessionAlias())
                .content(protoRawMessage.toByteArray())
                .timestamp(toInstant(metadata.getTimestamp()))
                .direction(toCradleDirection(messageID.getDirection()))
                .index(messageID.getSequence());
        addProperties(builder, protoRawMessage.getMetadata().getPropertiesMap());
        return builder.build();
    }
    public static int lenStr(String str) {
        return str != null ? str.getBytes(StandardCharsets.UTF_8).length : 0;
    }
    public static int calculateMessageSize(MessageToStore message) {
        int i = (message.getContent() != null ? message.getContent().length : 0) + MESSAGE_SIZE_CONST_VALUE;
        Map<String, String> md ;
        if (message.getMetadata() != null && (md = message.getMetadata().toMap()) != null) {
            for (Map.Entry<String, String> entry : md.entrySet()) {
                i += lenStr(entry.getKey())  // key
                        + lenStr(entry.getValue()) + 8; // value + 2 length
            }
        }
        return i;
    }
    public static int calculateMessageSizeInBatch(MessageToStore message) {
        return calculateMessageSize(message) + 4; //magic num
    }
    @Override
    public void close() throws Exception {
        batches.values().forEach(MessageBatch::close);
    }

    private class MessageBatch implements AutoCloseable {
        private final Lock lock = new ReentrantLock();
        private final RawMessageBatch.Builder batch = RawMessageBatch.newBuilder();
        private Future future = CompletableFuture.completedFuture(null);
        private long size = 0L;

        public void add(RawMessage message) {
            lock.lock();
            try {
                MessageToStore temp = toCradleMessage(message);
                int msgSize = calculateMessageSizeInBatch(temp);
                PrometheusMetrics.SENT_MESSAGES_SIZE
                        .labels(message.getMetadata().getId().getConnectionId().getSessionAlias(),
                                message.getMetadata().getId().getDirection().name())
                        .inc(msgSize);
                PrometheusMetrics.SENT_MESSAGES_NUMBER
                        .labels(message.getMetadata().getId().getConnectionId().getSessionAlias(),
                                message.getMetadata().getId().getDirection().name())
                        .inc();

                if (size + msgSize > maxBatchSize) {
                    send();
                }

                batch.addMessages(message);
                size += msgSize;

                if (batch.getMessagesCount() == 1) {
                    future = executorService.schedule(this::send, maxFlushTime, TimeUnit.MILLISECONDS);
                }

                if (size >= maxBatchSize) {
                    send();
                }
            } finally {
                lock.unlock();
            }
        }

        private void send() {
            if (batch.getMessagesCount() == 0) {
                return;
            }
            lock.lock();
            try {
                RawMessageBatch b = batch.build();
                if (checkMessageBatchSequenceGrowth) {
                    sequenceCheck(b.getMessagesList());
                }
                if (checkMessageBatchTimestampGrowth) {
                    timestampCheck(b.getMessagesList());
                }
                batch.clear();
                executorService.execute(() -> onBatch.accept(b));
                size = 0;
                future.cancel(false);
            } finally {
                lock.unlock();
            }
        }

        private void sequenceCheck(List<RawMessage> messages) {
            String sessionAlias = messages.get(0).getMetadata().getId().getConnectionId().getSessionAlias();
            Direction direction = messages.get(0).getMetadata().getId().getDirection();
            if (!IntStream.range(0, messages.size() - 1)
                    .allMatch(index -> messages.get(index).getMetadata().getId().getSequence() + 1 == messages.get(index + 1).getMetadata().getId().getSequence())) {
                if (logger.isErrorEnabled()) {
                    logger.error("Batch validation failed. {}:{} {} has elements with non incremental sequences", sessionAlias, direction, messages.stream()
                            .map(msg -> msg.getMetadata().getId().getSequence())
                            .collect(Collectors.toList()));
                }
            }
        }

        private void timestampCheck(List<RawMessage> messages) {
            String sessionAlias = messages.get(0).getMetadata().getId().getConnectionId().getSessionAlias();
            Direction direction = messages.get(0).getMetadata().getId().getDirection();
            if (!IntStream.range(0, messages.size() - 1)
                    .allMatch(index -> Timestamps.compare(messages.get(index).getMetadata().getTimestamp(), messages.get(index + 1).getMetadata().getTimestamp()) <= 0)) {
                if (logger.isErrorEnabled()) {
                    logger.error("Batch validation failed. {}:{} {} has elements with non incremental timestamps", sessionAlias, direction, messages.stream()
                            .map(msg -> msg.getMetadata().getId().getSequence())
                            .collect(Collectors.toList()));
                }
            }
        }

        @Override
        public void close() {
            send();
        }
    }
}
