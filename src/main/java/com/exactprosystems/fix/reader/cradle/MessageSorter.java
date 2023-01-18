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
import com.exactpro.th2.common.grpc.MessageID;
import com.exactpro.th2.common.grpc.RawMessage;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageSorter {
    private final Duration windowSize;
    private final Duration halfWindowSize;
    private final Duration connectionEndTime;
    private final long clearInterval;
    private long processedMessages = 0;
    private final MessageBatcher firstBatcher;
    private final MessageBatcher secondBatcher;
    private final Saver saver;
    private final Map<String, List<RawMessage.Builder>> messagesPerSessionAlias;
    private final Comparator<RawMessage.Builder> comparator = (o1, o2) -> Timestamps.compare(o1.getMetadata().getTimestamp(), o2.getMetadata().getTimestamp());

    public MessageSorter(Saver saver, MessageBatcher firstBatcher, MessageBatcher secondBatcher, long windowSize, long connectionEndTime, long clearInterval) {
        this.saver = saver;
        this.firstBatcher = firstBatcher;
        this.secondBatcher = secondBatcher;
        this.windowSize = Duration.newBuilder().setSeconds(windowSize / 1000).setNanos((int) ((windowSize % 1000) * 1000000)).build();
        this.halfWindowSize = Duration.newBuilder().setSeconds(windowSize / 2000).setNanos((int) (((windowSize / 2) % 1000) * 1000000)).build();
        this.connectionEndTime = Duration.newBuilder().setSeconds(connectionEndTime / 1000).setNanos((int) ((connectionEndTime % 1000) * 1000000)).build();
        this.clearInterval = clearInterval;
        messagesPerSessionAlias = new HashMap<>();
    }

    public void onMessagesRead(RawMessage.Builder message) {
        processedMessages++;
        List<RawMessage.Builder> messages = messagesPerSessionAlias.computeIfAbsent(message.getMetadata().getId().getConnectionId().getSessionAlias(),
                key -> new ArrayList<>());
        int ind = Collections.binarySearch(messages, message, comparator);
        if (ind >= 0) {
            messages.add(ind, message);
        } else {
            messages.add(-ind - 1, message);
        }
        Duration between = Timestamps.between(messages.get(0).getMetadata().getTimestamp(), messages.get(messages.size() - 1).getMetadata().getTimestamp());
        if (Durations.compare(between, windowSize) >= 0) {
            int lastInd = 0;
            Timestamp firstMessageTimestamp = messages.get(0).getMetadata().getTimestamp();
            for (int i = 0; i < messages.size(); i++) {
                RawMessage.Builder msg = messages.get(i);
                Timestamp subtract = Timestamps.subtract(msg.getMetadata().getTimestamp(), halfWindowSize);
                if (Timestamps.compare(subtract, firstMessageTimestamp) <= 0) {
                    prepareMessageAndSend(msg);
                } else {
                    lastInd = i;
                    break;
                }
            }
            messages.subList(0, lastInd).clear();
        }
        if (processedMessages % clearInterval == 0) {
            processStaleSessions(message.getMetadata().getTimestamp());
        }
    }

    public void processAll() {
        for (Map.Entry<String, List<RawMessage.Builder>> stringListEntry : messagesPerSessionAlias.entrySet()) {
            List<RawMessage.Builder> value = stringListEntry.getValue();
            for (RawMessage.Builder builder : value) {
                prepareMessageAndSend(builder);
            }
        }
        messagesPerSessionAlias.clear();
    }

    private void prepareMessageAndSend(RawMessage.Builder msg) {
        Direction direction = msg.getMetadata().getId().getDirection();
        String sessionAlias = msg.getMetadata().getId().getConnectionId().getSessionAlias();
        long nextSeqNum = saver.getNextSeqNum(sessionAlias, direction);
        ConnectionID connectionId = ConnectionID.newBuilder()
                .setSessionAlias(sessionAlias)
                .build();

        MessageID messageID = MessageID.newBuilder()
                .setConnectionId(connectionId)
                .setDirection(direction)
                .setSequence(nextSeqNum)
                .build();

        msg.getMetadataBuilder().setId(messageID);

        if (direction == Direction.FIRST) {
            firstBatcher.onMessage(msg.build());
        } else {
            secondBatcher.onMessage(msg.build());
        }
    }


    private void processStaleSessions(Timestamp timestamp) {
        List<String> toDelete = new ArrayList<>();
        for (Map.Entry<String, List<RawMessage.Builder>> stringListEntry : messagesPerSessionAlias.entrySet()) {
            List<RawMessage.Builder> value = stringListEntry.getValue();
            Duration between = Timestamps.between(value.get(value.size() - 1).getMetadata().getTimestamp(), timestamp);
            if (Durations.compare(between, connectionEndTime) >= 0) {
                toDelete.add(stringListEntry.getKey());
                for (RawMessage.Builder builder : value) {
                    prepareMessageAndSend(builder);
                }
            }
        }

        toDelete.forEach(messagesPerSessionAlias::remove);
    }

    public Map<String, List<RawMessage.Builder>> getMessagesPerSessionAlias() {
        return messagesPerSessionAlias;
    }
}
