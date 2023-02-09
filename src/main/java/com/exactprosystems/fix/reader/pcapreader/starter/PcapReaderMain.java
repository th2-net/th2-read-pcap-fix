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
package com.exactprosystems.fix.reader.pcapreader.starter;

import com.exactpro.cradle.CradleStorage;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.RawMessageBatch;
import com.exactpro.th2.common.schema.factory.CommonFactory;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactprosystems.fix.reader.cfg.IndividualReaderConfiguration;
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PcapReaderMain {
    private static final Logger log = LoggerFactory.getLogger(PcapReaderMain.class);

    public static void main(String[] args) {
        try(CommonFactory factory = CommonFactory.createFromArguments(args)) {
            PcapFileReaderConfiguration configuration = factory.getCustomConfiguration(PcapFileReaderConfiguration.class);

            try {
                CradleStorage storage = factory.getCradleManager().getStorage();
                long maxBatchSize = (long) (storage.getObjectsFactory().createMessageBatch().getSpaceLeft() * configuration.getUsableFractionOfBatchSize());
                MessageRouter<RawMessageBatch> messageRouter = null;
                MessageRouter<EventBatch> eventRouter = null;

                if (configuration.isUseMstore()) {
                    messageRouter = factory.getMessageRouterRawBatch();
                    eventRouter = factory.getEventBatchRouter();
                }

                if (configuration.isUseEventPublishing()) {
                    eventRouter = factory.getEventBatchRouter();
                }

                List<PcapReaderRunnable> readers = new ArrayList<>();

                for (IndividualReaderConfiguration individualReaderConfiguration : configuration.getIndividualReaderConfigurations()) {
                    readers.add(new PcapReaderRunnable(individualReaderConfiguration, configuration, storage, messageRouter, eventRouter, maxBatchSize));
                }

                List<Thread> threads = new ArrayList<>();

                for (PcapReaderRunnable reader : readers) {
                    Thread thread = new Thread(reader);
                    threads.add(thread);
                    thread.start();
                    log.info("ReaderThread {} started", thread.getName());
                }

                boolean foundDeadThread = false;
                while (Thread.currentThread().isAlive()) {
                    for (Thread thread : threads) {
                        if (!thread.isAlive()) {
                            foundDeadThread = true;
                            break;
                        }
                    }
                    if (foundDeadThread) {
                        break;
                    }
                    Thread.sleep(60 * 1000L);
                }

                for (PcapReaderRunnable reader : readers) {
                    reader.setAlive(false);
                }

                for (Thread thread : threads) {
                    thread.join();
                }

                System.exit(2);
            } catch (Exception e) {
                log.error("Fatal error", e);
                System.exit(1);
            }
        }
    }
}
