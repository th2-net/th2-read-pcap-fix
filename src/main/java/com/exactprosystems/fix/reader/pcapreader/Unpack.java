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

package com.exactprosystems.fix.reader.pcapreader;

import com.exactprosystems.fix.reader.pcapreader.constants.UnpackOption;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class Unpack {
    private static final int SIZE_40K = 8192 * 5;
    private static final int GZIP_MAGIC_BYTES_LENGTH = 2;
    private static final int XZ_MAGIC_BYTES_LENGTH = XZ.HEADER_MAGIC.length; // 6
    private static final int MAX_MAGIC_LENGTH = Math.max(XZ_MAGIC_BYTES_LENGTH, GZIP_MAGIC_BYTES_LENGTH);

    public static InputStream getInputStream(File file, UnpackOption unpackOption) throws IOException {
        return getInputStream(new BufferedInputStream(new FileInputStream(file), SIZE_40K), unpackOption);
    }

    public static InputStream getInputStream(InputStream inputStream, UnpackOption unpackOption) throws IOException {
        switch (unpackOption) {
            case NONE:
                return inputStream;
            case GZIP:
                return new GZIPInputStream(inputStream);
            case XZ:
                return new XZInputStream(inputStream);
            default:
                return autoUnpacked(inputStream);
        }
    }

    public static Path fileAuto(Path inFile, Path outFile) throws IOException {
        File in = maybeCompressed(inFile).toFile();
        try (InputStream is = getInputStream(in, UnpackOption.AUTO)) {
            Files.copy(is, outFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return outFile;
    }

    private static Path maybeCompressed(Path file) throws IOException {
        List<Path> filesLike, maybeCompressed;
        try (Stream<Path> stream = Files.walk(file.getParent(), 1)) {
            filesLike = stream
                    .filter(f -> !Files.isDirectory(f))
                    .filter(f -> f.toString().startsWith(file.toString()))
                    .collect(Collectors.toList());
        }
        if (filesLike.isEmpty()) {
            throw new IOException(String.format("File '%s*' not found", file));
        }
        if (filesLike.size() > 1) {
            maybeCompressed = filesLike.stream().filter(
                    f -> f.equals(file)
                            || f.equals(file.resolveSibling(file.getFileName() + ".xz"))
                            || f.equals(file.resolveSibling(file.getFileName() + ".gz")))
                    .collect(Collectors.toList());
        } else {
            maybeCompressed = filesLike;
        }
        if (maybeCompressed.isEmpty()) {
            throw new IOException(String.format("File '%s[.xz|.gz]' not found", file));
        }
        return maybeCompressed.get(0);
    }

    private static int readUntilBlocked(byte[] ba, InputStream is) throws IOException {
        int todo = ba.length;
        if (todo < 1) {
            throw new IllegalArgumentException("byte[].length < 1 makes no sense for this function");
        }
        int pos = 0;
        while (todo > 0 && is.available() > 0) {
            int read = is.read(ba, pos, todo);
            if (read == -1) {
                break;
            }
            pos += read;
            todo -= read;
        }
        return pos;
    }

    private static InputStream autoUnpacked(InputStream inputStream) throws IOException {
        byte[] magicBytes = new byte[MAX_MAGIC_LENGTH];
        int read;
        InputStream is;
        if (inputStream.markSupported()) {
            inputStream.mark(MAX_MAGIC_LENGTH);
            read = readUntilBlocked(magicBytes, inputStream);
            inputStream.reset();
            is = inputStream;
        } else {
            PushbackInputStream pbis = new PushbackInputStream(inputStream, MAX_MAGIC_LENGTH);
            read = readUntilBlocked(magicBytes, inputStream);
            if (read > 0) {
                pbis.unread(magicBytes, 0, read);
            }
            is = pbis;
        }

        if (isXZ(magicBytes, read)) {
            return new XZInputStream(is);
        } else if (isGzip(magicBytes, read)) {
            return new GZIPInputStream(is);
        } else {
            return is; // neither GZip nor XZ compression detected. Assuming Unpack.NONE
        }
    }

    private static boolean isXZ(byte[] magic, int readBytes) {
        if (readBytes < XZ_MAGIC_BYTES_LENGTH) {
            return false;
        }
        for (int i = 0; i < XZ_MAGIC_BYTES_LENGTH; i++) {
            if (magic[i] != XZ.HEADER_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isGzip(byte[] magicBytes, int length) {
        if (length < GZIP_MAGIC_BYTES_LENGTH) {
            return false;
        }
        int magic = (magicBytes[0] & 0xFF) | ((magicBytes[1] << 8) & 0xFF00);
        return magic == GZIPInputStream.GZIP_MAGIC;
    }
}
