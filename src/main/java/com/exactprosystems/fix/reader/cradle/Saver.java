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
import com.exactpro.th2.common.grpc.RawMessageMetadata;
import com.exactprosystems.fix.reader.connectionevents.ConnectionEvent;
import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.pcapreader.constants.ProtocolType;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStreamFactory;
import com.google.protobuf.Timestamp;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public abstract class Saver {
    public static final String PACKET_NUMBER_PROPERTY = "PACKET_NUMBER";
    public static final String FILE_NAME_PROPERTY = "FILE_NAME";
    public static final String SOURCE_IP_PROPERTY = "SOURCE_IP";
    public static final String SOURCE_PORT_PROPERTY = "SOURCE_PORT";
    public static final String DESTINATION_IP_PROPERTY = "DESTINATION_IP";
    public static final String DESTINATION_PORT_PROPERTY = "DESTINATION_PORT";

    protected static long startIndexCounter = 0;

    private Map<Pair<String, Direction>, Long> sequenceNums;

    protected TcpStreamFactory tcpStreamFactory;

    protected Saver(TcpStreamFactory tcpStreamFactory) {
        this.tcpStreamFactory = tcpStreamFactory;
        sequenceNums = new HashMap<>();
    }

    public long getNextSeqNum(String streamName, Direction direction) {
        Pair<String, Direction> pair = Pair.of(streamName, direction);
        long value = sequenceNums.getOrDefault(pair, startIndexCounter);
        sequenceNums.put(pair, value + 1);
        return value;
    }

    protected MessageID createMessageId(Direction direction, String streamName,
                                        long indexCounter) {
        ConnectionID connectionId = ConnectionID.newBuilder()
                .setSessionAlias(streamName)
                .build();

        return MessageID.newBuilder()
                .setConnectionId(connectionId)
                .setDirection(direction)
                .setSequence(indexCounter)
                .build();
    }

    protected RawMessageMetadata createMetadata(Direction direction, Map<String, String> properties, Instant time,
                                             ProtocolType protocol, PcapFileReaderConfiguration configuration,
                                             long indexCounter, String streamName) {
        MessageID messageId = createMessageId(direction, streamName, indexCounter);

        CollectionUtils.filter(properties.values(), Objects::nonNull);

        Timestamp timestamp;

        if (configuration.isUseTimestampFromPcap()) {
            timestamp = Timestamp.newBuilder().setSeconds(time.getEpochSecond())
                    .setNanos(time.getNano()).build();
        } else {
            long millis = System.currentTimeMillis();
            timestamp = Timestamp.newBuilder().setSeconds(millis / 1000)
                    .setNanos((int) ((millis % 1000) * 1000000)).build();
        }

        return RawMessageMetadata.newBuilder()
                .setId(messageId)
                .setTimestamp(timestamp)
                .putAllProperties(properties)
                .setProtocol(String.valueOf(protocol))
                .build();
    }

    protected RawMessageMetadata.Builder createMetadataBuilder(Direction direction, String streamName, Map<String, String> properties,
                                                               Instant time, ProtocolType protocolType, PcapFileReaderConfiguration configuration) {
        CollectionUtils.filter(properties.values(), Objects::nonNull);
        Timestamp timestamp;

        if (configuration.isUseTimestampFromPcap()) {
            timestamp = Timestamp.newBuilder().setSeconds(time.getEpochSecond())
                    .setNanos(time.getNano()).build();
        } else {
            long millis = System.currentTimeMillis();
            timestamp = Timestamp.newBuilder().setSeconds(millis / 1000)
                    .setNanos((int) ((millis % 1000) * 1000000)).build();
        }

        MessageID messageId = createMessageId(direction, streamName, 0);

        return RawMessageMetadata.newBuilder()
                .setTimestamp(timestamp)
                .putAllProperties(properties)
                .setId(messageId)
                .setProtocol(String.valueOf(protocolType));
    }

    public abstract void saveConnectionEvent(ConnectionEvent event, Pcap packet, boolean isClientStream, boolean saveToDb);

    protected String getSessionAlias(ConnectionEvent event, ProtocolType protocolType, boolean isClientStream) {
        String streamName = isClientStream ?
                tcpStreamFactory.getStreamName(ParsingUtils.ipToInt(event.getDestinationIP()), event.getDestinationPort()) :
                tcpStreamFactory.getStreamName(ParsingUtils.ipToInt(event.getSourceIP()), event.getSourcePort());

        if (StringUtils.isNotBlank(streamName)) {
            return streamName;
        }

        String source = event.getSourceIP() + ":" + event.getSourcePort();
        String destination = event.getDestinationIP() + ":" + event.getDestinationPort();
        return isClientStream ?
                protocolType.toString() + "_" + source + "_" + destination :
                protocolType.toString() + "_" + destination + "_" + source;
    }

}
