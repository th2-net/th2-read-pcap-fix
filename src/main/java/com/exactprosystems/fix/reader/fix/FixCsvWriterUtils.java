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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FixCsvWriterUtils {
    public static void createCSVFileWithHeaders() throws IOException {

        FileWriter csvOutputFile = new FileWriter("files/FIX.csv");
        String[] headers = new String[]{"Packet Number", "Timestamp", "File","Source IP", "Source Port", "Dest IP", "Dest Port", "VALUE"};
        PrintWriter pw = new PrintWriter(csvOutputFile);
        pw.println(convertToCSV(headers));
        csvOutputFile.close();
        pw.close();
    }

    public static String escapeSpecialCharactersCSV(String data) {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }
    public static String convertToCSV(String[] data) {
        return Stream.of(data)
                .map(FixCsvWriterUtils::escapeSpecialCharactersCSV)
                .collect(Collectors.joining(","));
    }
}
