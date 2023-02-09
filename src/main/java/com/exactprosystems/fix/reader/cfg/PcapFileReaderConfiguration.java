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

package com.exactprosystems.fix.reader.cfg;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public class PcapFileReaderConfiguration {
    private static final long DEFAULT_CHECKPOINT_INTERVAL = 10000;
    private static final long DEFAULT_DEAD_CONNECTIONS_SCAN_INTERVAL = 100000;
    private static final long DEFAULT_FIX_CONNECTION_DEATH_INTERVAL = 3L * 60 * 60 * 1000;
    private static final boolean DEFAULT_USE_SAVING_STATE = true;
    private static final boolean DEFAULT_USE_TIMESTAMP_FROM_PCAP = true;
    private static final long DEFAULT_SLEEP_INTERVAL = 60;
    private static final long DEFAULT_NUMBER_OF_PACKETS_TO_SUCCESSFUL_RESTORE = 10;
    private static final boolean DEFAULT_USE_EVENT_PUBLISHING = true;
    private static final boolean DEFAULT_USE_MSTORE = true;
    private static final boolean DEFAULT_USE_OFFSET_FROM_CRADLE = true;
    private static final boolean DEFAULT_DISABLE_CRADLE_SAVING = false;
    private static final boolean DEFAULT_DISABLE_CONNECTIVITY_MESSAGES = false;

    @JsonProperty("use_mstore")
    @JsonPropertyDescription("The flag that is responsible for saving messages to mstore instead of database")
    private boolean useMstore = DEFAULT_USE_MSTORE;

    @JsonProperty("checkpoint_interval")
    @JsonPropertyDescription("The number of packets after which the state will be saved")
    private long checkpointInterval = DEFAULT_CHECKPOINT_INTERVAL;

    @JsonProperty("dead_connections_scan_interval")
    private long deadConnectionsScanInterval = DEFAULT_DEAD_CONNECTIONS_SCAN_INTERVAL;

    @JsonProperty("connection_death_interval")
    @JsonPropertyDescription("This argument specifies the time after which the connection is considered dead")
    private long fixConnectionDeathInterval = DEFAULT_FIX_CONNECTION_DEATH_INTERVAL;

    @JsonProperty("read_state")
    private boolean readState = DEFAULT_USE_SAVING_STATE;

    @JsonProperty("write_state")
    private boolean writeState = DEFAULT_USE_SAVING_STATE;

    @JsonProperty("use_timestamp_from_pcap")
    private boolean useTimestampFromPcap = DEFAULT_USE_TIMESTAMP_FROM_PCAP;

    @JsonProperty("sleep_interval")
    private long sleepInterval = DEFAULT_SLEEP_INTERVAL;

    @JsonProperty("number_of_packets_to_successful_restore")
    private long numberOfPacketsToSuccessfulRestoreFIX = DEFAULT_NUMBER_OF_PACKETS_TO_SUCCESSFUL_RESTORE;

    @JsonProperty("use_event_publish")
    private boolean useEventPublishing = DEFAULT_USE_EVENT_PUBLISHING;

    @JsonProperty("disable_cradle_saving")
    private boolean disableCradleSaving = DEFAULT_DISABLE_CRADLE_SAVING;

    @JsonProperty("disable_connectivity_messages")
    private boolean disableConnectivityMessages = DEFAULT_DISABLE_CONNECTIVITY_MESSAGES;

    @JsonProperty("event_batch_size")
    private long eventBatchSize = 1024*1024L; // 1Mb

    @JsonProperty("event_batcher_core_pool_size")
    private int eventBatcherCorePoolSize = 100;

    @JsonProperty("event_batcher_max_flush_time")
    private long eventBatcherMaxFlushTime = 1000L;

    @JsonProperty("use_offset_from_cradle")
    private boolean useOffsetFromCradle = DEFAULT_USE_OFFSET_FROM_CRADLE;

    @JsonProperty("individual_read_configurations")
    private List<IndividualReaderConfiguration> individualReaderConfigurations;

    @JsonProperty("message_batcher_core_pool_size")
    private int messageBatcherCorePoolSize = 100;

    @JsonProperty("message_batcher_max_flush_time")
    private long messageBatcherMaxFlushTime = 1000L;

    @JsonProperty("buffered_reader_chunk_size")
    private int bufferedReaderChunkSize = 8192; //default value for BufferedInputStream

    @JsonProperty("check_message_batch_sequence_growth")
    private boolean checkMessageBatchSequenceGrowth = true;

    @JsonProperty("check_message_batch_timestamp_growth")
    private boolean checkMessageBatchTimestampGrowth = false;

    @JsonProperty("tcpdump_snapshot_length")
    private long tcpdumpSnapshotLength = 262144;

    @JsonProperty("sort_th2_messages")
    private boolean sortTh2Messages = false;

    @JsonProperty("usable_fraction_of_batch_size")
    private double usableFractionOfBatchSize = 0.9;

    @JsonProperty("message_sorter_window_size")
    private long messageSorterWindowSize = 15 * 1000L;

    @JsonProperty("message_sorter_connection_end_timeout")
    private long messageSorterConnectionEndTimeout = 30 * 1000L;

    @JsonProperty("message_sorter_clear_interval")
    private long messageSorterClearInterval = 2000;

    @JsonProperty("possible_time_window_between_files")
    private long possibleTimeWindowBetweenFiles = 1000;

    @JsonProperty("check_message_size_exceeds_batch_size")
    private boolean checkMessageSizeExceedsBatchSize = true;

    public List<IndividualReaderConfiguration> getIndividualReaderConfigurations() {
        return individualReaderConfigurations;
    }

    @SuppressWarnings("unused")
    public void setIndividualReaderConfigurations(List<IndividualReaderConfiguration> individualReaderConfigurations) {
        this.individualReaderConfigurations = individualReaderConfigurations;
    }

    public long getCheckpointInterval() {
        return checkpointInterval;
    }

    @SuppressWarnings("unused")
    public void setCheckpointInterval(long checkpointInterval) {
        this.checkpointInterval = checkpointInterval;
    }

    public boolean isReadState() {
        return readState;
    }

    @SuppressWarnings("unused")
    public void setReadState(boolean readState) {
        this.readState = readState;
    }

    public boolean isWriteState() {
        return writeState;
    }

    @SuppressWarnings("unused")
    public void setWriteState(boolean writeState) {
        this.writeState = writeState;
    }

    public boolean isUseTimestampFromPcap() {
        return useTimestampFromPcap;
    }

    @SuppressWarnings("unused")
    public void setUseTimestampFromPcap(boolean useTimestampFromPcap) {
        this.useTimestampFromPcap = useTimestampFromPcap;
    }

    public long getSleepInterval() {
        return sleepInterval;
    }

    @SuppressWarnings("unused")
    public void setSleepInterval(long sleepInterval) {
        this.sleepInterval = sleepInterval;
    }

    public boolean isUseEventPublishing() {
        return useEventPublishing;
    }

    @SuppressWarnings("unused")
    public void setUseEventPublishing(boolean useEventPublishing) {
        this.useEventPublishing = useEventPublishing;
    }

    public boolean isUseMstore() {
        return useMstore;
    }

    @SuppressWarnings("unused")
    public void setUseMstore(boolean useMstore) {
        this.useMstore = useMstore;

    }

    public boolean isUseOffsetFromCradle() {
        return useOffsetFromCradle;
    }

    @SuppressWarnings("unused")
    public void setUseOffsetFromCradle(boolean useOffsetFromCradle) {
        this.useOffsetFromCradle = useOffsetFromCradle;
    }

    public boolean isDisableCradleSaving() {
        return disableCradleSaving;
    }

    @SuppressWarnings("unused")
    public void setDisableCradleSaving(boolean disableCradleSaving) {
        this.disableCradleSaving = disableCradleSaving;
    }

    public boolean isDisableConnectivityMessages() {
        return disableConnectivityMessages;
    }

    @SuppressWarnings("unused")
    public void setDisableConnectivityMessages(boolean disableConnectivityMessages) {
        this.disableConnectivityMessages = disableConnectivityMessages;
    }

    public long getFixConnectionDeathInterval() {
        return fixConnectionDeathInterval;
    }

    @SuppressWarnings("unused")
    public void setFixConnectionDeathInterval(long fixConnectionDeathInterval) {
        this.fixConnectionDeathInterval = fixConnectionDeathInterval;
    }

    public long getNumberOfPacketsToSuccessfulRestoreFIX() {
        return numberOfPacketsToSuccessfulRestoreFIX;
    }

    @SuppressWarnings("unused")
    public void setNumberOfPacketsToSuccessfulRestoreFIX(long numberOfPacketsToSuccessfulRestoreFIX) {
        this.numberOfPacketsToSuccessfulRestoreFIX = numberOfPacketsToSuccessfulRestoreFIX;
    }

    public long getDeadConnectionsScanInterval() {
        return deadConnectionsScanInterval;
    }

    @SuppressWarnings("unused")
    public void setDeadConnectionsScanInterval(long deadConnectionsScanInterval) {
        this.deadConnectionsScanInterval = deadConnectionsScanInterval;
    }

    public int getMessageBatcherCorePoolSize() {
        return messageBatcherCorePoolSize;
    }

    @SuppressWarnings("unused")
    public void setMessageBatcherCorePoolSize(int messageBatcherCorePoolSize) {
        this.messageBatcherCorePoolSize = messageBatcherCorePoolSize;
    }

    public long getMessageBatcherMaxFlushTime() {
        return messageBatcherMaxFlushTime;
    }

    @SuppressWarnings("unused")
    public void setMessageBatcherMaxFlushTime(long messageBatcherMaxFlushTime) {
        this.messageBatcherMaxFlushTime = messageBatcherMaxFlushTime;
    }

    @SuppressWarnings("unused")
    public void setEventBatchSize(long eventBatchSize) {
        this.eventBatchSize = eventBatchSize;
    }

    public int getEventBatcherCorePoolSize() {
        return eventBatcherCorePoolSize;
    }

    @SuppressWarnings("unused")
    public void setEventBatcherCorePoolSize(int eventBatcherCorePoolSize) {
        this.eventBatcherCorePoolSize = eventBatcherCorePoolSize;
    }

    public long getEventBatcherMaxFlushTime() {
        return eventBatcherMaxFlushTime;
    }

    @SuppressWarnings("unused")
    public void setEventBatcherMaxFlushTime(long eventBatcherMaxFlushTime) {
        this.eventBatcherMaxFlushTime = eventBatcherMaxFlushTime;
    }

    public long getEventBatchSize() {
        return eventBatchSize;
    }

    public int getBufferedReaderChunkSize() {
        return bufferedReaderChunkSize;
    }

    @SuppressWarnings("unused")
    public void setBufferedReaderChunkSize(int bufferedReaderChunkSize) {
        this.bufferedReaderChunkSize = bufferedReaderChunkSize;
    }

    public boolean isCheckMessageBatchSequenceGrowth() {
        return checkMessageBatchSequenceGrowth;
    }

    @SuppressWarnings("unused")
    public void setCheckMessageBatchSequenceGrowth(boolean checkMessageBatchSequenceGrowth) {
        this.checkMessageBatchSequenceGrowth = checkMessageBatchSequenceGrowth;
    }

    public boolean isCheckMessageBatchTimestampGrowth() {
        return checkMessageBatchTimestampGrowth;
    }

    @SuppressWarnings("unused")
    public void setCheckMessageBatchTimestampGrowth(boolean checkMessageBatchTimestampGrowth) {
        this.checkMessageBatchTimestampGrowth = checkMessageBatchTimestampGrowth;
    }

    public long getTcpdumpSnapshotLength() {
        return tcpdumpSnapshotLength;
    }

    @SuppressWarnings("unused")
    public void setTcpdumpSnapshotLength(long tcpdumpSnapshotLength) {
        this.tcpdumpSnapshotLength = tcpdumpSnapshotLength;
    }

    public boolean isSortTh2Messages() {
        return sortTh2Messages;
    }

    @SuppressWarnings("unused")
    public void setSortTh2Messages(boolean sortTh2Messages) {
        this.sortTh2Messages = sortTh2Messages;
    }

    public long getMessageSorterWindowSize() {
        return messageSorterWindowSize;
    }

    @SuppressWarnings("unused")
    public void setMessageSorterWindowSize(long messageSorterWindowSize) {
        this.messageSorterWindowSize = messageSorterWindowSize;
    }

    public long getMessageSorterConnectionEndTimeout() {
        return messageSorterConnectionEndTimeout;
    }

    @SuppressWarnings("unused")
    public void setMessageSorterConnectionEndTimeout(long messageSorterConnectionEndTimeout) {
        this.messageSorterConnectionEndTimeout = messageSorterConnectionEndTimeout;
    }

    public long getMessageSorterClearInterval() {
        return messageSorterClearInterval;
    }

    @SuppressWarnings("unused")
    public void setMessageSorterClearInterval(long messageSorterClearInterval) {
        this.messageSorterClearInterval = messageSorterClearInterval;
    }

    public long getPossibleTimeWindowBetweenFiles() {
        return possibleTimeWindowBetweenFiles;
    }

    @SuppressWarnings("unused")
    public void setPossibleTimeWindowBetweenFiles(long possibleTimeWindowBetweenFiles) {
        this.possibleTimeWindowBetweenFiles = possibleTimeWindowBetweenFiles;
    }

    public boolean isCheckMessageSizeExceedsBatchSize() {
        return checkMessageSizeExceedsBatchSize;
    }

    @SuppressWarnings("unused")
    public void setCheckMessageSizeExceedsBatchSize(boolean checkMessageSizeExceedsBatchSize) {
        this.checkMessageSizeExceedsBatchSize = checkMessageSizeExceedsBatchSize;
    }

    public double getUsableFractionOfBatchSize() {
        return usableFractionOfBatchSize;
    }

    @SuppressWarnings("unused")
    public void setUsableFractionOfBatchSize(double usableFractionOfBatchSize) {
        this.usableFractionOfBatchSize = usableFractionOfBatchSize;
    }
}