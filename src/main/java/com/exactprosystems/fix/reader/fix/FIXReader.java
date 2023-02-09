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

import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.cradle.Saver;
import com.exactprosystems.fix.reader.pcapreader.Pcap;
import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FIXReader{
    private static final Logger log = LoggerFactory.getLogger(FIXReader.class);

    private Map<Integer, String> messageAssemblyMap;
    private List<FixListener> listeners = new ArrayList<>();

    private boolean restoreMode;
    private boolean saveToDb;

    private List<Pcap> packetsToRestore;
    private PcapFileReaderConfiguration configuration;
    private final String streamName;
    private final boolean useStaticStreamName;

    public FIXReader(Saver saver, PcapFileReaderConfiguration config, String streamName, boolean useStaticStreamName) throws IOException {
        listeners.add(new LoggingFixListener());
        listeners.add((FixListener) saver);
        listeners.add(FixCsvWriter.getInstance());
        messageAssemblyMap = new HashMap<>();
        configuration = config;
        this.streamName = streamName;
        this.useStaticStreamName = useStaticStreamName;
        this.packetsToRestore = new ArrayList<>();
    }

    public long readFix(ByteBuf payload, boolean isClientStream, Pcap associatedPacket, boolean saveToDb) throws IOException{
        String msg = payload.toString(StandardCharsets.UTF_8);
        int destinationIp = associatedPacket.header_ip4.destinationIpRaw;

        long messageCount = 0;

        String saved = messageAssemblyMap.remove(destinationIp);
        if (StringUtils.isNotBlank(saved)) {
            msg = saved + msg;
        }

        List<String> messages = splitFixMessage(msg);

        for (String message : messages) {
            if (checkMsg(message)) {
                FixMessage fixMsg = new FixMessage(associatedPacket, message, getSessionAlias(message, isClientStream));
                messageCount += notifyListeners(isClientStream, fixMsg, associatedPacket, saveToDb);
            } else {
                messageAssemblyMap.put(destinationIp, message);
            }
        }
        return messageCount;
    }

    private String getSessionAlias(String msg, boolean isClientStream) {
        if (useStaticStreamName) {
            return streamName;
        }
        int senderCompIdInd = msg.indexOf("\u000149=");
        int targetCompIdInd = msg.indexOf("\u000156=");

        if (senderCompIdInd != -1 && targetCompIdInd != -1) {
            int senderCompIdSohInd = msg.indexOf('\u0001', senderCompIdInd + 1);
            int targetCompIdSohInd = msg.indexOf('\u0001', targetCompIdInd + 1);
            if (senderCompIdSohInd != -1 && targetCompIdSohInd != -1) {
                String senderCompId = msg.substring(senderCompIdInd + 4, senderCompIdSohInd);
                String targetCompId = msg.substring(targetCompIdInd + 4, targetCompIdSohInd);
                return isClientStream ?
                        "FIX_" + senderCompId + "_" + targetCompId :
                        "FIX_" + targetCompId + "_" + senderCompId;
            }
        }
        return streamName;
    }

    public List<Pcap> restoreStream(boolean isClientStream,Pcap associatedPacket){
        resetState(!restoreMode);
        packetsToRestore.add(associatedPacket);

        int i = 0;
        int successfulPackets = 0;

        while (!packetsToRestore.isEmpty() && i < packetsToRestore.size() && successfulPackets < configuration.getNumberOfPacketsToSuccessfulRestoreFIX()) {
            Pcap packet = packetsToRestore.get(i);
            try {
                readFix(packet.effectivePayload,
                        packet.header_ip4.sourceIpRaw == associatedPacket.header_ip4.sourceIpRaw &&
                                packet.header_tcp.source_port == associatedPacket.header_tcp.source_port ? isClientStream : !isClientStream,
                        packet, false);
                i++;
                successfulPackets++;
            } catch (Exception e) {
                log.error("Error while restoring stream", e);
                packetsToRestore.remove(0);
                i = 0;
                successfulPackets = 0;
                resetState(false);
            }
        }

        if (successfulPackets >= configuration.getNumberOfPacketsToSuccessfulRestoreFIX()) {
            resetState(false);
            this.restoreMode = false;
            return packetsToRestore;
        }
        return Collections.emptyList();

    }

    public List<String> splitFixMessage(String msg) {
        List<String> res = new ArrayList<>();
        int i = 0;
        int k = msg.indexOf("8=FIX", i);
        while (k != -1) {
            int checkSum = msg.indexOf("\u000110=", k);
            if (checkSum != -1) {
                int end = msg.indexOf('\u0001', checkSum+1);
                if (end != -1) {
                    res.add(msg.substring(k, end+1));
                    int newK = msg.indexOf("8=FIX", end);
                    if (newK != -1) {
                        k = newK;
                    } else {
                        String toSave = msg.substring(end + 1);
                        if (StringUtils.isNotBlank(toSave)) {
                            res.add(toSave);
                        }
                        break;
                    }
                } else {
                    String toSave = msg.substring(k);
                    if (StringUtils.isNotBlank(toSave)) {
                        res.add(toSave);
                    }
                    break;
                }
            } else {
                String toSave = msg.substring(k);
                if (StringUtils.isNotBlank(toSave)) {
                    res.add(toSave);
                }
                break;
            }
        }
        return res;
    }

    public boolean checkMsg(String msg){
        if(msg.startsWith("8=FIX") && msg.contains("\u000110=") && msg.endsWith("\u0001")){
            return true;
        }
        return false;
    }

    public long notifyListeners(boolean isClientFrame, FixMessage msg, Pcap associatedPacket, boolean saveToDb) throws IOException {
        long messageCount = 0;
        for(FixListener temp : listeners){
            messageCount += temp.onMessagesRead(isClientFrame, msg, associatedPacket,saveToDb);
        }
        return messageCount;
    }

    public void resetState(boolean status){
        restoreMode = true;
        if(status){
            packetsToRestore.clear();
        }

    }

    public Map<Integer, String> getMessageAssemblyMap() {
        return messageAssemblyMap;
    }

    public List<FixListener> getListeners() {
        return listeners;
    }

    public void setMessageAssemblyMap(Map<Integer, String> messageAssemblyMap) {
        this.messageAssemblyMap = messageAssemblyMap;
    }

    public void setListeners(List<FixListener> listeners) {
        this.listeners = listeners;
    }

    public void setRestoreMode(boolean restoreMode) {
        this.restoreMode = restoreMode;
    }

    public boolean isRestoreMode() {
        return restoreMode;
    }

    public boolean isSaveToDb() {
        return saveToDb;
    }

    public List<Pcap> getPacketsToRestore() {
        return packetsToRestore;
    }

    public void setSaveToDb(boolean saveToDb) {
        this.saveToDb = saveToDb;
    }

    public void setPacketsToRestore(List<Pcap> packetsToRestore) {
        this.packetsToRestore = packetsToRestore;
    }
}
