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

import com.exactprosystems.fix.reader.cradle.AbstractMstoreSaver;

import java.util.List;
import java.util.Map;

public class MstoreSaverState {
    private Class<? extends AbstractMstoreSaver> clazz;
    private Map<String, List<RawMessageState>> messagesPerSessionAlias;

    public MstoreSaverState() {
    }

    public MstoreSaverState(Class<? extends AbstractMstoreSaver> clazz, Map<String, List<RawMessageState>> messagesPerSessionAlias) {
        this.clazz = clazz;
        this.messagesPerSessionAlias = messagesPerSessionAlias;
    }

    public Class<? extends AbstractMstoreSaver> getClazz() {
        return clazz;
    }

    public void setClazz(Class<? extends AbstractMstoreSaver> clazz) {
        this.clazz = clazz;
    }

    public Map<String, List<RawMessageState>> getMessagesPerSessionAlias() {
        return messagesPerSessionAlias;
    }

    public void setMessagesPerSessionAlias(Map<String, List<RawMessageState>> messagesPerSessionAlias) {
        this.messagesPerSessionAlias = messagesPerSessionAlias;
    }
}
