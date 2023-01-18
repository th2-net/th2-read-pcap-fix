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
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.grpc.EventStatus;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactprosystems.fix.reader.cfg.IndividualReaderConfiguration;
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.connectionevents.TcpConnectionEvent;
import com.exactprosystems.fix.reader.pcapreader.constants.ProtocolType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class EventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);

    private static final String FILE_NAME_PROPERTY = "file_name";
    private static final String PROTOCOLS_PROPERTY = "protocols_list";
    private static final String EXCEPTION_MESSAGE_PROPERTY = "exception_message";
    private static final String GAP_MESSAGE_PROPERTY = "gap_message";
    private static final String PCAP_FILE_SIZE_PROPERTY = "pcap_file_size";
    private static final String EVENT_TYPE_PROPERTY = "event_type";


    private final MessageRouter<EventBatch> eventBatchRouter;
    private final IndividualReaderConfiguration configuration;
    private ObjectMapper mapper;

    private final EventBatcher eventBatcher;

    public EventPublisher(MessageRouter<EventBatch> eventBatchRouter, PcapFileReaderConfiguration pcapFileReaderConfiguration, IndividualReaderConfiguration configuration) {
        this.eventBatchRouter = eventBatchRouter;
        this.configuration = configuration;
        mapper = new ObjectMapper();

        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(pcapFileReaderConfiguration.getEventBatcherCorePoolSize());

        eventBatcher = new EventBatcher(pcapFileReaderConfiguration.getEventBatchSize(),
                pcapFileReaderConfiguration.getEventBatcherMaxFlushTime(),
                scheduledExecutorService,
                batch -> {
                    try {
                        eventBatchRouter.sendAll(batch);
                    } catch (Exception e) {
                        if (logger.isErrorEnabled()) {
                            logger.error("Cannot send batch with ids: {}",
                                    batch.getEventsList().stream().map(Event::getId).collect(Collectors.toList()),
                                    e);
                        }
                    }
                });
    }

    public void publishExceptionEvent(String fileName,
                                      FileEventType fileEventType,
                                      String exceptionMessage) {
        Event.Builder eventBuilder = Event.newBuilder();
        eventBuilder.setId(EventID.newBuilder().setId(UUID.randomUUID().toString()).build());
        eventBuilder.setType(String.valueOf(fileEventType));
        eventBuilder.setStatus(fileEventType == FileEventType.ERROR ? EventStatus.FAILED : EventStatus.SUCCESS);
        long time = System.currentTimeMillis();
        eventBuilder.setStartTimestamp(Timestamp.newBuilder().setSeconds(time / 1000)
                .setNanos((int) ((time % 1000) * 1000000)).build());

        eventBuilder.setName(fileName + "_" + fileEventType);

        Map<String, Object> body = new HashMap<>();
        body.put(FILE_NAME_PROPERTY, fileName);
        body.put(EXCEPTION_MESSAGE_PROPERTY, exceptionMessage);
        body.put(EVENT_TYPE_PROPERTY, EventType.EXCEPTION_EVENT);

        try {
            eventBuilder.setBody(ByteString.copyFromUtf8(mapper.writeValueAsString(body)));
            eventBatcher.onEvent(eventBuilder.build());
        } catch (IOException e) {
            logger.error("Error while sending event batch", e);
        }
    }

    public void publishEvent(String fileName,
                             FileEventType fileEventType,
                             long startTime,
                             long endTime,
                             long pcapFileSize,
                             List<ProtocolType> protocols,
                             String exceptionMessage) {
        Event.Builder eventBuilder = Event.newBuilder();
        eventBuilder.setId(EventID.newBuilder().setId(UUID.randomUUID().toString()).build());
        eventBuilder.setType(String.valueOf(fileEventType));
        eventBuilder.setStatus(fileEventType == FileEventType.ERROR ? EventStatus.FAILED : EventStatus.SUCCESS);

        Timestamp startTimestamp = Timestamp.newBuilder().setSeconds(startTime / 1000)
                .setNanos((int) ((startTime % 1000) * 1000000)).build();
        Timestamp endTimestamp = Timestamp.newBuilder().setSeconds(endTime / 1000)
                .setNanos((int) ((endTime % 1000) * 1000000)).build();

        eventBuilder.setStartTimestamp(startTimestamp);
        eventBuilder.setEndTimestamp(endTimestamp);

        eventBuilder.setName(fileName + "_" + fileEventType);


        Map<String, Object> body = new HashMap<>();
        body.put(FILE_NAME_PROPERTY, fileName);
        body.put(PROTOCOLS_PROPERTY, protocols);
        body.put(PCAP_FILE_SIZE_PROPERTY, pcapFileSize);
        body.put(EVENT_TYPE_PROPERTY, EventType.FILE_EVENT);

        if (StringUtils.isNotBlank(exceptionMessage)) {
            body.put(EXCEPTION_MESSAGE_PROPERTY, exceptionMessage);
        }

        try {
            eventBuilder.setBody(ByteString.copyFromUtf8(mapper.writeValueAsString(body)));
            eventBatcher.onEvent(eventBuilder.build());
        } catch (IOException e) {
            logger.error("Error while sending event batch", e);
        }
    }

    public void publishGapEvent (TcpConnectionEvent gapEvent, String fileName, String beforeGapPacket, long packetNum) {
        Event.Builder eventBuilder = Event.newBuilder();
        eventBuilder.setId(EventID.newBuilder().setId(UUID.randomUUID().toString()).build());
        eventBuilder.setType(FileEventType.ERROR.name());
        eventBuilder.setStatus(EventStatus.FAILED);
        String s = String.format("Connection %s:%d->%s%d gap",
                gapEvent.getSourceIP(),
                gapEvent.getSourcePort(),
                gapEvent.getDestinationIP(),
                gapEvent.getDestinationPort());

        long time = System.currentTimeMillis();
        eventBuilder.setStartTimestamp(Timestamp.newBuilder().setSeconds(time / 1000)
                .setNanos((int) ((time % 1000) * 1000000)).build());

        eventBuilder.setName(s);

        String message = String.format("Before gap packet: %s; After gap packet: %s",
                StringUtils.isBlank(beforeGapPacket) ? "UNKNOWN" : beforeGapPacket,
                fileName + " #" + packetNum);

        Map<String, Object> body = new HashMap<>();
        body.put(FILE_NAME_PROPERTY, fileName);
        body.put(GAP_MESSAGE_PROPERTY, message);
        body.put(EVENT_TYPE_PROPERTY, EventType.GAP_EVENT);

        try {
            eventBuilder.setBody(ByteString.copyFromUtf8(mapper.writeValueAsString(body)));
            eventBatcher.onEvent(eventBuilder.build());
        } catch (IOException e) {
            logger.error("Error while sending event batch", e);
        }
    }

    public void publishNewFilesEvent(List<String> files, String lastReadFile) {
        long startTime = System.currentTimeMillis();
        Timestamp startTimestamp = Timestamp.newBuilder().setSeconds(startTime / 1000)
                .setNanos((int) ((startTime % 1000) * 1000000)).build();

        for (String s : files) {
            if (Objects.equals(s, lastReadFile)) {
                continue;
            }
            Event.Builder eventBuilder = Event.newBuilder();
            eventBuilder.setId(EventID.newBuilder().setId(UUID.randomUUID().toString()).build());
            eventBuilder.setType(String.valueOf(FileEventType.NEW));
            eventBuilder.setStatus(EventStatus.SUCCESS);
            eventBuilder.setName(s + "_" + FileEventType.NEW);

            eventBuilder.setStartTimestamp(startTimestamp);

            Map<String, String> body = new HashMap<>();
            body.put(FILE_NAME_PROPERTY, s);

            try {
                eventBuilder.setBody(ByteString.copyFromUtf8(mapper.writeValueAsString(body)));
            } catch (JsonProcessingException e) {
                logger.error("Error while converting body to JSON", e);
            }

            eventBatcher.onEvent(eventBuilder.build());
        }
    }
}
