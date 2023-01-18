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

package com.exactprosystems.fix.reader.eventsender;

import com.exactpro.th2.common.grpc.Event;
import com.exactpro.th2.common.grpc.EventBatch;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class EventBatcher implements AutoCloseable {
    private final long maxBatchSize;
    private final long maxFlushTime;
    private final ScheduledExecutorService executorService;
    private final Consumer<EventBatch> onBatch;

    private final InnerEventBatch innerEventBatch;

    public EventBatcher(long maxBatchSize,
                        long maxFlushTime,
                        ScheduledExecutorService executorService,
                        Consumer<EventBatch> onBatch) {
        this.maxBatchSize = maxBatchSize;
        this.maxFlushTime = maxFlushTime;
        this.executorService = executorService;
        this.onBatch = onBatch;

        innerEventBatch = new InnerEventBatch();
    }

    public void onEvent(Event event) {
        innerEventBatch.add(event);
    }

    @Override
    public void close() throws Exception {
        innerEventBatch.close();
    }

    private class InnerEventBatch implements AutoCloseable {
        private final Lock lock = new ReentrantLock();
        private final EventBatch.Builder batch = EventBatch.newBuilder();
        private Future future = CompletableFuture.completedFuture(null);
        private long size = 0L;

        public void add(Event event) {
            lock.lock();
            try {
                batch.addEvents(event);
                size += event.getBody().size();

                if (batch.getEventsCount() == 1) {
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
            if (batch.getEventsCount() == 0) {
                return;
            }
            lock.lock();
            try {
                EventBatch b = batch.build();
                executorService.execute(() -> onBatch.accept(b));
                batch.clear();
                size = 0;
                future.cancel(false);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() {
            send();
        }
    }
}
