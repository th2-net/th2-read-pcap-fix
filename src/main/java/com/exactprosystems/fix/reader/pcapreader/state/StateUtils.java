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
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStream;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStreamFactory;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.google.common.collect.Maps;

import io.netty.buffer.ByteBuf;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class StateUtils implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(StateUtils.class);

    private AtomicReference<State> stateObject;
    private final String stateFileName;
    private volatile boolean alive;

    public StateUtils(String fileName) {
        this.stateFileName = fileName;
        stateObject = new AtomicReference<>();
        alive = true;
    }

    public State loadState(TcpStreamFactory tcpStreamFactory) {
        logger.info("Start of loading state procedure");
        CBORMapper objectMapper = new CBORMapper();

        SimpleModule module = new SimpleModule();
        module.addSerializer(ByteBuf.class, new ByteBufSerializer(ByteBuf.class));
        module.addDeserializer(ByteBuf.class, new ByteBufDeserializer(ByteBuf.class));
        objectMapper.registerModule(module);

        File file = new File(stateFileName);
        if (!file.exists()) {
            return null;
        }

        State state = null;
        try {
            state = objectMapper.readValue(file, State.class);
            Map<String, List<ConnectionAddressState>> streamNameConnectionAddressesMap = state.getStreamNameConnectionAddressesMap();
            if (streamNameConnectionAddressesMap != null && !streamNameConnectionAddressesMap.isEmpty()) {
                if (!Objects.deepEquals(streamNameConnectionAddressesMap, tcpStreamFactory.getStreamNameConnectionAddressesMap())) {
                    logger.warn("Connection addresses from state and connection addresses from configuration are not equal: {}",
                            Maps.difference(streamNameConnectionAddressesMap, tcpStreamFactory.getStreamNameConnectionAddressesMap()).entriesDiffering());
                }
            }
            State finalState = state;
            tcpStreamFactory.getStreams().forEach(key -> {
                try {
                    key.loadState(finalState);
                } catch (IOException e) {
                    logger.error("Error while loading state", e);
                }
            });
            tcpStreamFactory.getSavers().stream()
                    .filter(AbstractMstoreSaver.class::isInstance)
                    .forEach(key -> ((AbstractMstoreSaver) key).loadState(finalState));
        } catch (IllegalStateException e) {
            logger.error("Error while loading state", e);
            throw e;
        } catch (Exception e) {
            logger.error("Error while loading state", e);
        }
        logger.info("End of loading state procedure");
        return state;
    }

    /**
     * Generates state and schedules it to be stored in the file
     */
    public void saveState(TcpStreamFactory tcpStreamFactory, String lastReadFile, long offset) {
        State state = collectState(tcpStreamFactory, lastReadFile, offset);
        saveState(state);
    }

    /**
     * Schedules state to be stored in the file
     */
    public void saveState(State state) {
        stateObject.set(state);
    }

    /**
     * Generates state using {@link TcpStreamFactory}
     */
    @NotNull
    public static State collectState(TcpStreamFactory tcpStreamFactory, String lastReadFile, long offset) {
        List<TcpStreamState> tcpStreamStates = new ArrayList<>();
        tcpStreamFactory.getStreams().stream()
                .map(TcpStream::getState)
                .forEach(tcpStreamStates::add);
        List<MstoreSaverState> saverStates = new ArrayList<>();
        tcpStreamFactory.getSavers().stream()
                .filter(AbstractMstoreSaver.class::isInstance)
                .map(key -> ((AbstractMstoreSaver) key).getState())
                .forEach(saverStates::add);
        return new State(lastReadFile, offset, tcpStreamStates, saverStates, tcpStreamFactory.getStreamNameConnectionAddressesMap());
    }

    @Override
    public void run() {
        CBORMapper mapper = new CBORMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(ByteBuf.class, new ByteBufSerializer(ByteBuf.class));
        module.addDeserializer(ByteBuf.class, new ByteBufDeserializer(ByteBuf.class));
        mapper.registerModule(module);


        while (alive) {
            try {
                if (stateObject.get() == null) {
                    Thread.sleep(250L);
                    continue;
                }

                State state = stateObject.getAndSet(null);
                String lastReadFile = state.getLastReadFile();
                long offset = state.getOffset();
                String preFileName = stateFileName + UUID.randomUUID();
                File stateFile = new File(preFileName);
                if (stateFile.exists()) {
                    boolean delete = stateFile.delete();
                    if (!delete) {
                        logger.error("Unable to delete old state file {}", stateFile.getName());
                    }
                }
                File stateParentDir = stateFile.getParentFile();
                if (stateParentDir != null && !stateParentDir.exists()) {
                    logger.info("Creating required dirs for state file {}", stateFile);
                    if (!stateParentDir.mkdirs()) {
                        logger.warn("Couldn't create parent dirs for state file {}", stateFile);
                    }
                }
                mapper.writeValue(stateFile, state);
                File oldFile = new File(stateFileName);
                if (oldFile.exists()) {
                    boolean delete = oldFile.delete();
                    if (!delete) {
                        logger.error("Unable to delete old state file {}", oldFile.getName());
                    }
                }
                boolean rename = stateFile.renameTo(oldFile);
                if (!rename) {
                    logger.error("Unable to rename state file from {} to {}", stateFile.getName(), oldFile.getName());
                }
                logger.info("State with last read file {} and offset {} saved", lastReadFile, offset);
            } catch (Exception e) {
                logger.error("Error in StateUtils thread", e);
            }
        }
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }
}
