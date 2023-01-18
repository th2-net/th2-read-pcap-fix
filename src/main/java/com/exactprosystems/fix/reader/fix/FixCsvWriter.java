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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class FixCsvWriter implements FixListener{

    private static FixCsvWriter instance;

    private FixCsvWriter() throws IOException {
        FixCsvWriterUtils.createCSVFileWithHeaders();
    }

    public static FixCsvWriter getInstance() throws IOException {
        if (instance == null) {
            instance = new FixCsvWriter();
        }
        return instance;
    }

    @Override
    public long onMessagesRead(boolean isClientFrame, FixMessage msg, Pcap associatedPacket, boolean saveToDb) throws IOException {
        List<String[]> dataLines = new ArrayList<>();
        Integer sourcePort = associatedPacket.header_tcp.source_port;
        Integer destinationPort = associatedPacket.header_tcp.destination_port;
        Long packetNumLong = associatedPacket.packetNum;
        dataLines.add(new String[]
                {
                        packetNumLong.toString(),
                        associatedPacket.timestamp.toString(),
                        associatedPacket.source,

                        ParsingUtils.intToIp(associatedPacket.header_ip4.sourceIpRaw),
                        sourcePort.toString(),
                        ParsingUtils.intToIp(associatedPacket.header_ip4.destinationIpRaw),
                        destinationPort.toString(),
                        msg.getData(),
                });
        FileWriter csvOutputFile = new FileWriter("files/FIX.csv", true);
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {

            dataLines.stream()
                    .map(FixCsvWriterUtils::convertToCSV)
                    .forEach(pw::println);
        }
        csvOutputFile.close();
        return 0;
    }
}
