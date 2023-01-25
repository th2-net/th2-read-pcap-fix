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

package com.exactprosystems.fix.reader.pcapreader.starter;

import com.exactpro.cradle.CradleStorage;
import com.exactpro.cradle.Direction;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.RawMessageBatch;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactprosystems.fix.reader.cfg.IndividualReaderConfiguration;
import com.exactprosystems.fix.reader.cfg.PcapFileReaderConfiguration;
import com.exactprosystems.fix.reader.cradle.AbstractCradleSaver;
import com.exactprosystems.fix.reader.eventsender.EventPublisher;
import com.exactprosystems.fix.reader.eventsender.FileEventType;
import com.exactprosystems.fix.reader.pcapreader.FileReader;
import com.exactprosystems.fix.reader.pcapreader.PacketSource;
import com.exactprosystems.fix.reader.pcapreader.PcapFileReader;
import com.exactprosystems.fix.reader.pcapreader.state.State;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStreamFactory;
import com.exactprosystems.fix.reader.prometheus.PrometheusMetrics;
import com.exactprosystems.fix.reader.pcapreader.state.StateUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PcapReaderRunnable implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(PcapReaderRunnable.class);

    private final IndividualReaderConfiguration individualReaderConfiguration;
    private final PcapFileReaderConfiguration pcapFileReaderConfiguration;
    private final CradleStorage storage;
    private final MessageRouter<RawMessageBatch> messageRouter;
    private final MessageRouter<EventBatch> eventRouter;
    private volatile boolean alive;
    private final long maxBatchSize;

    public PcapReaderRunnable(IndividualReaderConfiguration individualReaderConfiguration,
                              PcapFileReaderConfiguration pcapFileReaderConfiguration,
                              CradleStorage storage,
                              MessageRouter<RawMessageBatch> messageRouter,
                              MessageRouter<EventBatch> eventRouter,
                              long maxBatchSize) {
        this.individualReaderConfiguration = individualReaderConfiguration;
        this.pcapFileReaderConfiguration = pcapFileReaderConfiguration;
        this.storage = storage;
        this.messageRouter = messageRouter;
        this.eventRouter = eventRouter;
        this.alive = true;
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public void run() {
        String dir = individualReaderConfiguration.getPcapDirectory();
        String prefix = individualReaderConfiguration.getPcapFilePrefix();
        long checkpoint = pcapFileReaderConfiguration.getCheckpointInterval();

        if (individualReaderConfiguration.getConnectionAddresses() == null || individualReaderConfiguration.getConnectionAddresses().isEmpty()) {
            log.error("Connection addresses were not specified");
            return;
        }

        File directory = new File(dir);
        if (!directory.exists()) {
            log.error("Directory {} does not exist", dir);
            return;
        }

        if (!directory.isDirectory()) {
            log.error("{} is not a directory", dir);
            return;
        }

        individualReaderConfiguration.setPcapDirectoryName(directory.getName());
        individualReaderConfiguration.setPcapDirectoryPath(directory.getAbsolutePath());

        Long offsetFromCradle = AbstractCradleSaver.initSequenceNumber(storage, individualReaderConfiguration.getPcapFilePrefix(), pcapFileReaderConfiguration.isUseOffsetFromCradle());
        if (!pcapFileReaderConfiguration.isUseOffsetFromCradle()) {
            offsetFromCradle = null;
        }


        EventPublisher eventPublisher = null;

        if (pcapFileReaderConfiguration.isUseEventPublishing()) {
            eventPublisher = new EventPublisher(eventRouter, pcapFileReaderConfiguration, individualReaderConfiguration);
        }


        TcpStreamFactory tcpStreamFactory = new TcpStreamFactory(pcapFileReaderConfiguration,
                individualReaderConfiguration,
                storage,
                messageRouter,
                eventRouter,
                eventPublisher,
                maxBatchSize);

        StateUtils stateUtils = new StateUtils(individualReaderConfiguration.getStateFileName());
        try (PcapFileReader pcapReader = new PcapFileReader(tcpStreamFactory, "PCAP", pcapFileReaderConfiguration, individualReaderConfiguration, stateUtils)) {
            String lastReadFile = null;
            long lastFileSize = Long.MIN_VALUE;
            Instant lastFileLastPacketTimestamp = null;
            Instant lastFileFirstPacketTime = null;
            long lastFileReadPackets = 0;
            Set<String> parsedFiles = new HashSet<>();
            boolean firstIteration = true;
            Thread stateUtilsThread = new Thread(stateUtils);

            State state = pcapFileReaderConfiguration.isReadState() ? stateUtils.loadState(tcpStreamFactory) : null;
            stateUtilsThread.start();

            lastReadFile = state == null ? null : state.getLastReadFile();
            boolean rescanDirectory = false;
            while (alive) {
                String[] directoryContents = directory.list();
                List<PcapFileEntry> filesToRead = new ArrayList<>();
                for (String fileName : directoryContents) {
                    File file = new File(directory.getPath() + File.separator + fileName);
                    if (!file.isFile()) {
                        log.warn("Skip {}. Not a file", file.getAbsolutePath());
                        continue;
                    }
                    if (fileName.endsWith("tar.gz")) {
                        if (individualReaderConfiguration.isReadTarGzArchives() && (individualReaderConfiguration.getTarGzArchivePrefix() == null || fileName.startsWith(individualReaderConfiguration.getTarGzArchivePrefix()))) {
                            filesToRead.addAll(getFilesFromTarGzArchive(file, pcapReader, parsedFiles, eventPublisher));
                        }
                    } else if (!parsedFiles.contains(fileName) && (prefix == null || fileName.startsWith(prefix))) {
                        try (var input = getInputStream(directory.getPath(), fileName)) {
                            PacketSource source = new PacketSource(fileName, input);
                            Instant timestamp = pcapReader.getFirstPacketTimestamp(source, fileName, true);
                            filesToRead.add(new PcapFileEntry(fileName, directory.getAbsolutePath(), timestamp, false));
                        } catch (Exception e) {
                            String s = String.format("Unable to identify first packet timestamp of the file %s. File will be skipped", directory.getPath() + File.separator + fileName);
                            log.error(s, e);
                            if (eventPublisher != null) {
                                eventPublisher.publishExceptionEvent(directory.getPath() + File.separator + fileName, FileEventType.ERROR, s);
                            }
                        }
                    }
                }

                if (lastFileFirstPacketTime == null) {
                    if (state != null && state.getLastReadFile() != null) {
                        try (var input = getInputStream(directory.getPath(), state.getLastReadFile())) {
                            lastFileFirstPacketTime = pcapReader.getFirstPacketTimestamp(
                                    new PacketSource(
                                            state.getLastReadFile(),
                                            input),
                                    state.getLastReadFile(),
                                    true);
                        }
                    }

                    if (lastFileFirstPacketTime == null) {
                        log.info("Unable to identify last read file. All files in the directory will be proceed");
                        lastFileFirstPacketTime = Instant.MIN;
                    }
                }

                Instant finalLastFileFirstPacketTime = lastFileFirstPacketTime;
                String finalLastReadFile = lastReadFile;
                filesToRead.stream()
                        .filter(pcapFileEntry -> pcapFileEntry.getFirstPacketTimestamp().isAfter(finalLastFileFirstPacketTime) ||
                                pcapFileEntry.getFilename().equals(finalLastReadFile));

                List<PcapFileEntry> files = new ArrayList<>();


                for (PcapFileEntry pcapFileEntry : filesToRead) {
                    if (parsedFiles.contains(pcapFileEntry.getFilename()) && !pcapFileEntry.getFilename().equals(lastReadFile)) {
                        continue;
                    }
                    if (pcapFileEntry.getFirstPacketTimestamp() != null && (pcapFileEntry.getFirstPacketTimestamp().isAfter(finalLastFileFirstPacketTime) ||
                            pcapFileEntry.getFilename().equals(finalLastReadFile))) {
                        files.add(pcapFileEntry);
                    } else {
                        parsedFiles.add(pcapFileEntry.getFilename());
                    }
                }
                files.sort(Comparator.comparing(PcapFileEntry::getFirstPacketTimestamp));

                    /*if (eventPublisher != null) {
                        eventPublisher.publishNewFilesEvent(filesToRead, lastReadFile);
                    }*/
                String lastReadFileFromState = state == null ? null : state.getLastReadFile();
                Long offsetFromState = state == null ? null : state.getOffset();
                boolean stateNotExists = state == null;
                state = null; // TODO: the state is reset here. Why is it happens?
                if(stateNotExists && firstIteration){
                    for (String name: tcpStreamFactory.getStreamNames()
                    ) {
                        if(storage.getLastMessageIndex(name, Direction.FIRST) != -1 ||
                                storage.getLastMessageIndex(name, Direction.SECOND) != -1){
                            throw new Exception("there are already messages in the cradle for session alias: " + name);
                        }
                    }
                }
                firstIteration = false;
                for (PcapFileEntry pcapFileEntry : files) {
                    if (!alive) {
                        stateUtils.setAlive(false);
                        return;
                    }
                    String fileName = pcapFileEntry.getFilename();
                    long offset = fileName.equals(lastReadFileFromState) ?
                            offsetFromState :
                            fileName.equals(lastReadFile) ? lastFileReadPackets : 0;

                    if (stateNotExists || !fileName.equals(lastReadFileFromState) || (offsetFromCradle != null && offset > offsetFromCradle)) {
                        offsetFromCradle = null;
                    }

                    if (fileName.contains("tar.gz" + File.separator)) {
                        long fileSize = getFileSize(directory.getPath(), fileName);
                        if (Objects.equals(lastReadFile, fileName)) {
                            if (lastFileSize == fileSize) {
                                log.info("Size of file {} has not changed. No need in parsing", fileName);
                                continue;
                            }
                        }
                        lastFileSize = fileSize;
                    } else {
                        File file = new File(directory.getPath() + File.separator + fileName);

                        if (!file.exists()) {
                            log.error("File {} has disappeared. Directory will be rescanned", file.getAbsolutePath());
                            rescanDirectory = true;
                            break;
                        }

                        if (Objects.equals(lastReadFile, fileName) && lastFileSize == file.length()) {
                            log.info("Size of file {} has not changed. No need in parsing", fileName);
                            continue;
                        }
                        lastFileSize = file.length();
                    }
                    log.info("Parsing of the file {} has started", fileName);
                    long startTime = System.currentTimeMillis();

                    FileReader.ParseResult result;
                    try (InputStream is = getInputStream(directory.getPath(), fileName)) {
                        PacketSource source = new PacketSource(directory.getAbsolutePath() + File.separator + fileName, is);
                        result = pcapReader.readAndParseAndStoreToDb(source,
                                checkpoint,
                                offset,
                                offsetFromCradle,
                                eventPublisher,
                                fileName.equals(lastReadFile) ? null : lastFileLastPacketTimestamp,
                                null,
                                null);
                    }

                    long endTime = System.currentTimeMillis();
                    tcpStreamFactory.printSkippedInfo();
                    tcpStreamFactory.printUnknownAddresses();
                    log.info("Parsed file {} with result {}. Spent time: {} s", fileName, result, (endTime - startTime) / 1000);

                    PrometheusMetrics.READ_FILE_SIZE.labels(fileName).inc(result.getBytesRead());

                    if (result.isSuccess() && eventPublisher != null) {
                        eventPublisher.publishEvent(directory.getAbsolutePath() + File.separator + fileName,
                                FileEventType.SYNCHRONIZED,
                                startTime,
                                endTime,
                                result.getBytesRead(),
                                result.getProtocols(),
                                null);
                    }

                    tcpStreamFactory.closeDeadConnections();
                    if (pcapFileReaderConfiguration.isWriteState()) {
                        if (!stateUtilsThread.isAlive()) {
                            stateUtilsThread = new Thread(stateUtils);
                            log.error("State Utils thread was interrupted. Restarting thread");
                            stateUtilsThread.start();
                        }
                        stateUtils.saveState(tcpStreamFactory, fileName, result.getPacketsRead());
                    }
                    tcpStreamFactory.resetSavedInfo();
                    lastReadFile = fileName;
                    if (result.getFirstPacketTimestamp() != null) {
                        lastFileFirstPacketTime = result.getFirstPacketTimestamp();
                    }
                    if (result.getLastPacketTimestamp() != null) {
                        lastFileLastPacketTimestamp = result.getLastPacketTimestamp();
                    }
                    lastFileReadPackets = result.getPacketsRead();
                    parsedFiles.add(fileName);
                }
                if (rescanDirectory) {
                    rescanDirectory = false;
                    continue;
                }
                log.info("Reader with path '{}' and prefix '{}'. All files were processed. Save all data and go to sleep for {} seconds",
                        individualReaderConfiguration.getPcapDirectory(),
                        individualReaderConfiguration.getPcapFilePrefix(),
                        pcapFileReaderConfiguration.getSleepInterval());
                long startTime = System.currentTimeMillis();
                tcpStreamFactory.triggerSavers();
                long endTime = System.currentTimeMillis();
                Thread.sleep(Math.max(pcapFileReaderConfiguration.getSleepInterval() * 1000 - (endTime - startTime), 0));
            }
        } catch (Exception e) {
            log.error("Fatal error", e);
        }
    }

    private long getFileSize(String path, String fileName) throws IOException {
        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(path + File.separator + fileName.substring(0, fileName.lastIndexOf("tar.gz" + File.separator) + 6))))) {
            TarArchiveEntry currentEntry = tarInput.getNextTarEntry();
            while (currentEntry != null) {
                if (Objects.equals(currentEntry.getName(), fileName.substring(fileName.lastIndexOf("tar.gz" + File.separator) + 7))) {
                    return currentEntry.getSize();
                }
                currentEntry = tarInput.getNextTarEntry();
            }
        }
        throw new FileNotFoundException("File " + path + File.separator + fileName + " does not exist");
    }

    private InputStream getInputStream(String path, String fileName) throws IOException {
        if (fileName.contains("tar.gz" + File.separator)) {
            TarArchiveInputStream tarInput = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(path + File.separator + fileName.substring(0, fileName.lastIndexOf("tar.gz" + File.separator) + 6))));
            TarArchiveEntry currentEntry = tarInput.getNextTarEntry();
            while (currentEntry != null) {
                if (Objects.equals(currentEntry.getName(), fileName.substring(fileName.lastIndexOf("tar.gz" + File.separator) + 7))) {
                    return new BufferedInputStream(tarInput, pcapFileReaderConfiguration.getBufferedReaderChunkSize());
                }
                currentEntry = tarInput.getNextTarEntry();
            }
            tarInput.close();
        } else {
            return new BufferedInputStream(new FileInputStream(path + File.separator + fileName), pcapFileReaderConfiguration.getBufferedReaderChunkSize());
        }
        throw new FileNotFoundException("File " + path + File.separator + fileName + " does not exist");
    }

    private List<PcapFileEntry> getFilesFromTarGzArchive(File file, PcapFileReader pcapFileReader, Set<String> parsedFiles, EventPublisher eventPublisher) throws IOException {
        List<PcapFileEntry> result = new ArrayList<>();
        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(file)))) {
            TarArchiveEntry currentEntry = tarInput.getNextTarEntry();
            while (currentEntry != null) {
                String fileName = file.getName() + File.separator + currentEntry.getName();
                if (!parsedFiles.contains(fileName) && (individualReaderConfiguration.getPcapFilePrefix() == null || currentEntry.getName().startsWith(individualReaderConfiguration.getPcapFilePrefix()))) {
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(tarInput);
                    PacketSource source = new PacketSource(fileName, bufferedInputStream);
                    try {
                        Instant firstPacketTimestamp = pcapFileReader.getFirstPacketTimestamp(source, fileName, false);
                        result.add(new PcapFileEntry(fileName, file.getAbsolutePath(), firstPacketTimestamp, true));
                    } catch (Exception e) {
                        String s = String.format("Unable to identify first packet timestamp of the file %s. File will be skipped", file.getAbsolutePath() + File.separator + fileName);
                        log.error(s, e);
                        if (eventPublisher != null) {
                            eventPublisher.publishExceptionEvent(file.getAbsolutePath() + File.separator + fileName, FileEventType.ERROR, s);
                        }
                    }
                }
                currentEntry = tarInput.getNextTarEntry();
            }
        }
        return result;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }
}
