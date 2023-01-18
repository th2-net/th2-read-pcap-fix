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

package com.exactprosystems.fix.reader.pcapreader.state;

import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FixReaderInternalState {

    private int sourceHost;
    private int sourcePort;

    private int destinationHost;
    private int destinationPort;

    private Map<Integer, String> messageMap;

    private boolean saveToDb;
    private boolean restoreMode;

    private List<Pcap> packetsToRestore;

    public FixReaderInternalState() {
    }

    public FixReaderInternalState(int sourceHost,
                                  int sourcePort,
                                  int destinationHost,
                                  int destinationPort,
                                  Map<Integer, String> messageMap,
                                  boolean saveToDb,
                                  boolean restoreMode,
                                  List<Pcap> packetsToRestore) {
        this.sourceHost = sourceHost;
        this.sourcePort = sourcePort;
        this.destinationHost = destinationHost;
        this.destinationPort = destinationPort;
        this.messageMap = messageMap;
        this.saveToDb = saveToDb;
        this.restoreMode = restoreMode;
        this.packetsToRestore = packetsToRestore;
    }

    @JsonIgnore
    public String getTCPIPId() {
        String source = ParsingUtils.intToIp(sourceHost) + ":" + sourcePort;
        String destination = ParsingUtils.intToIp(destinationHost) + ":" + destinationPort;
        return source + "_" + destination;
    }

    public int getSourceHost() {
        return sourceHost;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public int getDestinationHost() {
        return destinationHost;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public Map<Integer, String> getMessageMap() {
        return messageMap;
    }

    public boolean isSaveToDb() {
        return saveToDb;
    }

    public boolean isRestoreMode() {
        return restoreMode;
    }

    public List<Pcap> getPacketsToRestore() {
        return packetsToRestore;
    }

    public void setSourceHost(int sourceHost) {
        this.sourceHost = sourceHost;
    }

    @Deprecated
    public void setSourceHost(String sourceHost) {
        this.sourceHost = ParsingUtils.ipToInt(sourceHost);
    }

    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }

    public void setDestinationHost(int destinationHost) {
        this.destinationHost = destinationHost;
    }

    @Deprecated
    public void setDestinationHost(String destinationHost) {
        this.destinationHost = ParsingUtils.ipToInt(destinationHost);
    }

    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
    }

    public void setMessageMap(Map<Object, String> messageMap) {
        if (messageMap == null || messageMap.isEmpty()) {
            this.messageMap = Collections.emptyMap();
            return;
        }
        this.messageMap = new HashMap<>();
        for (Map.Entry<Object, String> objectStringEntry : messageMap.entrySet()) {
            if (objectStringEntry.getKey() instanceof Number) {
                this.messageMap.put(((Number) objectStringEntry.getKey()).intValue(), objectStringEntry.getValue());
            } else if (objectStringEntry.getKey() instanceof String) {
                this.messageMap.put(ParsingUtils.ipToInt((String) objectStringEntry.getKey()), objectStringEntry.getValue());
            }
        }
    }

    public void setSaveToDb(boolean saveToDb) {
        this.saveToDb = saveToDb;
    }

    public void setRestoreMode(boolean restoreMode) {
        this.restoreMode = restoreMode;
    }

    public void setPacketsToRestore(List<Pcap> packetsToRestore) {
        this.packetsToRestore = packetsToRestore;
    }
}
