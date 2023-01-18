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
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.List;

public class IndividualReaderConfiguration {
    private static final String DEFAULT_PCAP_DIRECTORY = "/opt/pcap";
    private static final String DEFAULT_PCAP_FILE_PREFIX = "dump";

    @JsonProperty("pcap_directory")
    @JsonPropertyDescription("Directory with pcap files")
    private String pcapDirectory = DEFAULT_PCAP_DIRECTORY;

    private String pcapDirectoryName;
    private String pcapDirectoryPath;

    @JsonProperty("pcap_file_prefix")
    @JsonPropertyDescription("Prefix of the pcap files")
    private String pcapFilePrefix = DEFAULT_PCAP_FILE_PREFIX;

    @JsonProperty("read_tar_gz_archives")
    private boolean readTarGzArchives = false;

    @JsonProperty("tar_gz_archive_prefix")
    private String tarGzArchivePrefix;


    @JsonProperty("connection_addresses")
    private List<ConnectionAddress> connectionAddresses;

    @JsonProperty("state_file_path")
    private String stateFilePath;

    public String getPcapDirectory() {
        return pcapDirectory;
    }

    public void setPcapDirectory(String pcapDirectory) {
        this.pcapDirectory = pcapDirectory;
    }

    public String getPcapDirectoryName() {
        return pcapDirectoryName;
    }

    public void setPcapDirectoryName(String pcapDirectoryName) {
        this.pcapDirectoryName = pcapDirectoryName;
    }

    public String getPcapDirectoryPath() {
        return pcapDirectoryPath;
    }

    public void setPcapDirectoryPath(String pcapDirectoryPath) {
        this.pcapDirectoryPath = pcapDirectoryPath;
    }

    public String getPcapFilePrefix() {
        return pcapFilePrefix;
    }

    public void setPcapFilePrefix(String pcapFilePrefix) {
        this.pcapFilePrefix = pcapFilePrefix;
    }

    public List<ConnectionAddress> getConnectionAddresses() {
        return connectionAddresses;
    }

    public void setConnectionAddresses(List<ConnectionAddress> connectionAddresses) {
        this.connectionAddresses = connectionAddresses;
    }

    public String getStateFilePath() {
        return stateFilePath;
    }

    public String getStateFileName() {
        String pcapDirectoryPathChanged = pcapDirectory.replace(File.separator, "_");
        return StringUtils.isBlank(stateFilePath) ?
                String.format("State_%s_%s.bin", pcapDirectoryPathChanged, pcapFilePrefix) :
                String.format("%s%sState_%s_%s.bin", stateFilePath, File.separator, pcapDirectoryPathChanged, pcapFilePrefix);
    }

    public void setStateFilePath(String stateFilePath) {
        this.stateFilePath = stateFilePath;
    }

    public boolean isReadTarGzArchives() {
        return readTarGzArchives;
    }

    public void setReadTarGzArchives(boolean readTarGzArchives) {
        this.readTarGzArchives = readTarGzArchives;
    }

    public String getTarGzArchivePrefix() {
        return tarGzArchivePrefix;
    }

    public void setTarGzArchivePrefix(String tarGzArchivePrefix) {
        this.tarGzArchivePrefix = tarGzArchivePrefix;
    }
}
