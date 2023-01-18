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

import java.util.List;
import java.util.Map;

public class State {
    private String lastReadFile;
    private long offset;

    private List<TcpStreamState> tcpStreamStates;
    private List<MstoreSaverState> mstoreSaverStates;
    private Map<String, List<ConnectionAddressState>> streamNameConnectionAddressesMap;


    public State() {
    }

    public State(String lastReadFile,
                 long offset,
                 List<TcpStreamState> tcpStreamStates,
                 List<MstoreSaverState> mstoreSaverStates,
                 Map<String, List<ConnectionAddressState>> streamNameConnectionAddressesMap) {
        this.lastReadFile = lastReadFile;
        this.offset = offset;
        this.tcpStreamStates = tcpStreamStates;
        this.mstoreSaverStates = mstoreSaverStates;
        this.streamNameConnectionAddressesMap = streamNameConnectionAddressesMap;
    }

    public String getLastReadFile() {
        return lastReadFile;
    }

    public void setLastReadFile(String lastReadFile) {
        this.lastReadFile = lastReadFile;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public List<TcpStreamState> getTcpStreamStates() {
        return tcpStreamStates;
    }

    public void setTcpStreamStates(List<TcpStreamState> tcpStreamStates) {
        this.tcpStreamStates = tcpStreamStates;
    }

    public List<MstoreSaverState> getMstoreSaverStates() {
        return mstoreSaverStates;
    }

    public void setMstoreSaverStates(List<MstoreSaverState> mstoreSaverStates) {
        this.mstoreSaverStates = mstoreSaverStates;
    }

    public Map<String, List<ConnectionAddressState>> getStreamNameConnectionAddressesMap() {
        return streamNameConnectionAddressesMap;
    }

    public void setStreamNameConnectionAddressesMap(Map<String, List<ConnectionAddressState>> streamNameConnectionAddressesMap) {
        this.streamNameConnectionAddressesMap = streamNameConnectionAddressesMap;
    }
}
