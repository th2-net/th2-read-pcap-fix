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

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;

// http://mindprod.com/jgloss/masking.html
// http://www.darksleep.com/player/JavaAndUnsignedTypes.html

public final class ParsingUtils {
    private static final Logger logger = LoggerFactory.getLogger(ParsingUtils.class);

    private static final String[] _zeros = new String[]{
            "", "0", "00", "000", "0000", "00000", "000000", "0000000", "00000000", "0000000"
    };
    private static final String[] spaces = makeSpaces();
    private static final String hex = "0123456789abcdef";
    private static final byte endl = (byte) 0;
    private static final byte space = (byte) ' ';
    private static String characters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final Map<Integer, String> ipCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 1000;
        }
    };

    public static int ipToInt(String ipAddress) {
        int result = 0;
        String[] ipAddressInArray = ipAddress.split("\\.");
        for (int i = 3; i >= 0; i--) {
            long ip = Integer.parseInt(ipAddressInArray[3 - i]);
            result |= ip << (i * 8);
        }
        ipCache.put(result, ipAddress);
        return result;
    }

    public static String intToIp(int ip) {
        return ipCache.computeIfAbsent(ip, key -> ((key >> 24) & 0xFF) + "."
                + ((key >> 16) & 0xFF) + "."
                + ((key >> 8) & 0xFF) + "."
                + (key & 0xFF));
    }

    private static String[] makeSpaces() {
        String[] spaces = new String[200];
        spaces[0] = "";

        for (int i = 1; i < spaces.length; i++) {
            spaces[i] = spaces[i - 1] + "0";
        }
        return spaces;
    }

    public static int getUInt8(byte b) {
        return (0xFF & b);
    }

    public static int getUInt16(byte[] b) {
        if (b.length == 2) {
            return (0xFF & b[0]) << 8 | (0xFF & b[1]);
        } else {
            return -1;
        }
    }

    public static String byteToHex(byte b) {
        return hex.charAt((b & 0xF0) >> 4) + "" + hex.charAt(b & 0x0F);
    }

    public static String byteBufToHexString(ByteBuf buf) {
        byte[] ba = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), ba);
        return arrayToHexString(ba);
    }

    public static String arrayToHexString(byte[] ba) {
        if (ba == null || ba.length == 0)
            return "";

        StringBuilder result = new StringBuilder(2 * ba.length);

        for (final byte b : ba) {
            result.append(hex.charAt((b & 0xF0) >> 4))
                    .append(hex.charAt((b & 0x0F))).append(' ');
        }
        return result.toString();
    }

    public static String byteBufToHex(ByteBuf buf) {
        byte[] ba = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), ba);
        return arrayToHex(ba);
    }

    public static String arrayToHex(byte[] byteRaw) {
        if (byteRaw != null) {
            StringWriter bufferHEX = new StringWriter();
            StringWriter bufferRAW = new StringWriter();

            for (int i = 0; i < byteRaw.length; i++) {
                if (i % 16 == 0) {
                    if (i != 0) {
                        bufferHEX.write("\t" + bufferRAW.toString());
                        bufferRAW = new StringWriter();
                        bufferHEX.write("\r\n");
                    }

                    String addrHexValue = Integer.toHexString(i);
                    bufferHEX.write(_zeros[8 - addrHexValue.length()]
                            + addrHexValue + "\t");
                }

                if (byteRaw[i] != (byte) 0 && byteRaw[i] != '\r'
                        && byteRaw[i] != '\n' && byteRaw[i] != '\t') {
                    String latin1byte = "";
                    try {
                        latin1byte = new String(new byte[]{byteRaw[i]},
                                "KOI8-R");
                    } catch (UnsupportedEncodingException e) {
                        logger.error("ArrayToHEX enconding error", e);
                    }
                    bufferRAW.write(latin1byte);
                } else {
                    bufferRAW.write(" ");
                }

                bufferHEX.write(byteToHex(byteRaw[i]) + " ");
            }

            String trailer = bufferRAW.toString();

            if (trailer.length() != 0) {
                int trailerSpaces = 16 - trailer.length();

                for (int i = 0; i < trailerSpaces; i++) {
                    bufferHEX.write("   ");
                }
                bufferHEX.write("\t" + trailer);
            }

            return bufferHEX.toString();
        } else {
            return "";
        }
    }

    public static byte[] merge2ByteArrays(byte[] ba1, byte[] ba2) {
        if (ba1 == null && ba2 == null) {
            return new byte[0];
        } else if (ba1 == null) {
            return ba2;
        } else if (ba2 == null) {
            return ba1;
        } else {
            byte[] result = new byte[ba1.length + ba2.length];
            System.arraycopy(ba1, 0, result, 0, ba1.length);
            System.arraycopy(ba2, 0, result, ba1.length, ba2.length);
            return result;
        }
    }
}
