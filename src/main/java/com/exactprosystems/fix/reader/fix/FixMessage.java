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

package com.exactprosystems.fix.reader.fix;

import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import com.exactprosystems.fix.reader.pcapreader.state.DefaultInstantDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;

import java.time.Instant;

public class FixMessage {

    private String data;
    private String associatedPacketFileName;
    private long associatedPacketNumber;
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = DefaultInstantDeserializer.class)
    private Instant associatedPacketTimestamp;
    private String sourceIP;
    private int sourcePort;
    private String destinationIP;
    private int destinationPort;
    private String streamName;

    public FixMessage() {
    }

    public FixMessage(Pcap associatedPacket, String data, String streamName){
        this.associatedPacketFileName = associatedPacket.source;
        this.associatedPacketNumber = associatedPacket.packetNum;
        this.associatedPacketTimestamp = associatedPacket.timestamp;
        this.data = data;
        this.streamName = streamName;
        this.sourceIP = ParsingUtils.intToIp(associatedPacket.header_ip4.sourceIpRaw);
        this.destinationIP = ParsingUtils.intToIp(associatedPacket.header_ip4.destinationIpRaw);
        this.sourcePort = associatedPacket.header_tcp.source_port;
        this.destinationPort = associatedPacket.header_tcp.destination_port;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public long getAssociatedPacketNumber() {
        return associatedPacketNumber;
    }

    public void setAssociatedPacketNumber(long associatedPacketNumber) {
        this.associatedPacketNumber = associatedPacketNumber;
    }

    public Instant getAssociatedPacketTimestamp() {
        return associatedPacketTimestamp;
    }

    public void setAssociatedPacketTimestamp(Instant associatedPacketTimestamp) {
        this.associatedPacketTimestamp = associatedPacketTimestamp;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getAssociatedPacketFileName() {
        return associatedPacketFileName;
    }

    public void setAssociatedPacketFileName(String associatedPacketFileName) {
        this.associatedPacketFileName = associatedPacketFileName;
    }

    public String getSourceIP() {
        return sourceIP;
    }

    public void setSourceIP(String sourceIP) {
        this.sourceIP = sourceIP;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }

    public String getDestinationIP() {
        return destinationIP;
    }

    public void setDestinationIP(String destinationIP) {
        this.destinationIP = destinationIP;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
    }
}
