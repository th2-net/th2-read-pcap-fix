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

import com.exactpro.cradle.CradleManager;
import com.exactpro.cradle.CradleStorage;
import com.exactpro.cradle.Direction;
import com.exactpro.cradle.messages.StoredMessage;
import com.exactpro.cradle.messages.StoredMessageId;
import com.exactpro.cradle.utils.CradleStorageException;
import com.exactpro.th2.common.grpc.RawMessage;
import com.exactpro.th2.common.schema.factory.CommonFactory;
import com.exactprosystems.fix.reader.cfg.IndividualReaderConfiguration;
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.cradle.AbstractCradleSaver;
import com.exactprosystems.fix.reader.pcapreader.FileReader;
import com.exactprosystems.fix.reader.pcapreader.PacketSource;
import com.exactprosystems.fix.reader.pcapreader.PcapFileReader;
import com.exactprosystems.fix.reader.pcapreader.state.State;
import com.exactprosystems.fix.reader.pcapreader.state.StateUtils;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStreamFactory;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import java.util.stream.Collectors;

@Disabled
public class PcapReaderTest {

    private static final Logger log = LoggerFactory.getLogger(PcapReaderTest.class);
    private  CommonFactory factory;

    @BeforeAll
    public static void initTests() throws IOException, InterruptedException {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
    }

    @AfterEach
    public void cleanUp() {
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    public void fixTest() throws IOException, InterruptedException, CradleStorageException {
        factory = CommonFactory.createFromArguments("-c",
                "cradleConfidentialConfiguration",
                "customConfiguration",
                "--cradleConfidentialConfiguration",
                "src/test/resources/fixTest/cradle.json",
                "--customConfiguration",
                "src/test/resources/fixTest/custom.json");
        execute(factory, null, "fix.pcap", false);

        List<StoredMessage> messages = getAllMessages();
        StringBuilder stringBuilder = new StringBuilder();
        for (StoredMessage message : messages) {
            stringBuilder.append(RawMessage.parseFrom(message.getContent()).getDefaultInstanceForType());
        }

//        Files.write(Path.of("src/test/resources/fixTest/fixTestData"), stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
        Assertions.assertArrayEquals(Files.readAllBytes(Path.of("src/test/resources/fixTest/fixTestData")), stringBuilder.toString().getBytes(StandardCharsets.UTF_8));

    }

    private List<StoredMessage> getAllMessages() throws IOException {
        List<StoredMessage> result = new ArrayList<>();
        for (String streamName : factory.getCradleManager().getStorage().getStreams()) {
            long lastFirstMessageIndex = factory.getCradleManager().getStorage().getLastMessageIndex(streamName, Direction.FIRST);
            long lastSecondMessageIndex = factory.getCradleManager().getStorage().getLastMessageIndex(streamName, Direction.SECOND);

            for (int i = 0; i <= Math.max(lastFirstMessageIndex, lastSecondMessageIndex); i++) {
                StoredMessage messageFirst = factory.getCradleManager().getStorage().getMessage(new StoredMessageId(streamName, Direction.FIRST, i));
                StoredMessage messageSecond = factory.getCradleManager().getStorage().getMessage(new StoredMessageId(streamName, Direction.SECOND, i));
                if (messageFirst != null) {
                    result.add(messageFirst);
                }
                if (messageSecond != null) {
                    result.add(messageSecond);
                }
            }
        }
        return result;
    }

    public void execute(CommonFactory factory, Long stopAfter, String stopFileName, boolean loadState) throws IOException, CradleStorageException, InterruptedException {
        PcapFileReaderConfiguration configuration = factory.getCustomConfiguration(PcapFileReaderConfiguration.class);
        IndividualReaderConfiguration individualReaderConfiguration = configuration.getIndividualReaderConfigurations().get(0);

        String dir = individualReaderConfiguration.getPcapDirectory();
        String prefix = individualReaderConfiguration.getPcapFilePrefix();
        long checkpoint = configuration.getCheckpointInterval();

        CradleManager manager = factory.getCradleManager();
        try {
            CradleStorage storage = manager.getStorage();
            AbstractCradleSaver.resetSequenceCounters();
            Long offsetFromCradle = AbstractCradleSaver.initSequenceNumber(storage, individualReaderConfiguration.getPcapFilePrefix(), true);

            TcpStreamFactory tcpStreamFactory = new TcpStreamFactory(configuration,
                    individualReaderConfiguration,
                    storage,
                    null,
                    null,
                    null,
                    storage.getObjectsFactory().createMessageBatch().getSpaceLeft());

            File directory = new File(dir);
            if (!directory.exists()) {
                log.error("Directory {} does not exist", dir);
                return;
            }

            if (!directory.isDirectory()) {
                log.error("{} is not a directory", dir);
                return;
            }

            StateUtils stateUtils = new StateUtils(individualReaderConfiguration.getStateFileName());
            try (PcapFileReader pcapReader = new PcapFileReader(tcpStreamFactory, "PCAP", configuration, individualReaderConfiguration, stateUtils)) {
                String lastReadFile = null;
                while (Thread.currentThread().isAlive()) {
                    String[] directoryContents = directory.list();
                    List<String> filesToRead = new ArrayList<>();
                    for (String fileName : directoryContents) {
                        if (prefix == null || fileName.startsWith(prefix)) {
                            filesToRead.add(fileName);
                        }
                    }
                    filesToRead.sort(String::compareTo);
                    Thread stateUtilsThread = new Thread(stateUtils);
                    State state = loadState ? stateUtils.loadState(tcpStreamFactory) : null;
                    stateUtilsThread.start();

                    String finalLastReadFile = lastReadFile;
                    filesToRead = filesToRead.stream()
                            .filter(key -> (prefix == null || key.startsWith(prefix)) && (state == null || key.compareTo(state.getLastReadFile()) >= 0))
                            .filter(key -> (finalLastReadFile == null || key.compareTo(finalLastReadFile) >= 0))
                            .collect(Collectors.toList());
                    for (int i = 0; i < filesToRead.size(); i++) {
                        String fileName = filesToRead.get(i);
                        long offset = state != null && fileName.equals(state.getLastReadFile()) ? state.getOffset() : 0;

                        if (state == null || !fileName.equals(state.getLastReadFile()) || offset > offsetFromCradle) {
                            offsetFromCradle = null;
                        }


                        InputStream is = Files.newInputStream(Path.of(directory.getPath() + File.separator + fileName));
                        PacketSource source = new PacketSource(fileName, is);
                        FileReader.ParseResult result = pcapReader.readAndParseAndStoreToDb(source,
                                checkpoint,
                                offset,
                                offsetFromCradle,
                                null,
                                null,
                                stopAfter,
                                stopFileName);
                        tcpStreamFactory.triggerSaversAndWait();
                        if (stopAfter != null) {
                            return;
                        }
                        log.info("Parsed file {} with result {}", fileName, result);
                        if (configuration.isWriteState()) {
                            stateUtils.saveState(tcpStreamFactory, fileName, result.getPacketsRead());
                        }
                        tcpStreamFactory.resetSavedInfo();
                        lastReadFile = fileName;
                        return;
                    }

                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error while reading pcap", e);
        }
    }
}
