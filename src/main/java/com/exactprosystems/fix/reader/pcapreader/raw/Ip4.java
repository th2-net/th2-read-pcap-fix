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
package com.exactprosystems.fix.reader.pcapreader.raw;

import com.exactprosystems.fix.reader.pcapreader.ParsingUtils;
import com.exactprosystems.fix.reader.pcapreader.constants.Protocol;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ip4 extends ProtocolHeader implements Cloneable {
    private static final Logger logger = LoggerFactory.getLogger(Ip4.class);

    public ByteBuf header;
    public Protocol protocol;
    public byte protocolRaw;
    public int length, totalLength;
    public int sourceIpRaw, destinationIpRaw;

    // Detailed
    public int intIdentification, intFragmentOffset;
    public String tos, identification, fragmentOffset, ttl, checksum, flagsValue;
    public int version, ihl, reservd, df, mf;

    public String hex_version, hex_ihl, hex_tos, hex_totalLength, hex_identification, hex_flags, hex_fragmentOffset, hex_ttl, hex_checksum;
    public String hex_protocol, hex_source_ip, hex_destination_ip;

    @JsonIgnore
    public void setHeader(ByteBuf data) {
        // http://en.wikipedia.org/wiki/IPv4
        // http://www.inetdaemon.com/tutorials/internet/ip/datagram_structure.shtml

        // 000 - Version 4
        // 000 - IHL 4					- where payload data begins
        // 001 - TOS 8
        // 002 - Total length 16
        // 004 - Identification 16
        // 006 - flags 3
        // 006 - fragment offset 13
        // 008 - TTL 8
        // 009 - Protocol 8
        // ICMP = 1
        // IGMP = 2
        // TCP = 6
        // UDP = 17
        // 010 - HeaderChecksum 16
        // 012 - Source Address 32
        // 016 - Destination Address 32

        header = data;
        length = data.readableBytes();

        totalLength = header.getUnsignedShort(2);

        if (data.getByte(9) == (byte) 6) {
            protocol = Protocol.TCP;
        } else if (data.getByte(9) == (byte) 17) {
            protocol = Protocol.UDP;
        } else if (data.getByte(9) == (byte) 1) {
            protocol = Protocol.ICMP;
        } else if (data.getByte(9) == (byte) 2) {
            protocol = Protocol.IGMP;
        } else {
            protocol = null;
            protocolRaw = data.getByte(9);
        }

        sourceIpRaw = data.getInt(12);
        destinationIpRaw = data.getInt(16);
    }

    @Override
    public void print() {
        logger.info("   IPv4 header:");
        logger.info("      Protocol: {}", protocol == null ? protocolRaw : protocol);
        logger.info("     source_ip: {}", sourceIpRaw);
        logger.info("destination_ip: {}", destinationIpRaw);
        logger.info("          Data: ");
        if (logger.isInfoEnabled()) {
            logger.info(ParsingUtils.byteBufToHex(header));
        }
    }

    public void parseNecessaryInfo() {
        if (header != null && header.readableBytes() >= 20) {
            try {
                intIdentification = header.getUnsignedShort(4);

                byte subByte = header.getByte(6);
                reservd = (subByte >>> 7) & 0x1;
                df = (subByte >>> 6) & 0x1;
                mf = (subByte >>> 5) & 0x1;

                byte[] subData = new byte[2];
                header.getBytes(6, subData);
                subData[0] = (byte) (subData[0] >>> 3);
                intFragmentOffset = ParsingUtils.getUInt16(subData) & 0xff;
            } catch (Exception e) {
                logger.error("Can't parse header data. ", e);
            }
        }
    }

    @Override
    public void parseDetailedInfo() {
        if (header != null && header.readableBytes() >= 20) {
            try {
                byte[] subData = null;

                version = (header.getByte(0) >>> 4) & 0xf;
                hex_version = ParsingUtils.byteToHex(header.getByte(0));

                ihl = header.getByte(0) & 0xf;
                hex_ihl = ParsingUtils.byteToHex(header.getByte(0));

                tos = "" + ParsingUtils.getUInt8(header.getByte(1));
                hex_tos = ParsingUtils.byteToHex(header.getByte(1));

                subData = new byte[2];
                header.getBytes(2, subData);
                hex_totalLength = ParsingUtils.arrayToHexString(subData);

                header.getBytes(4, subData);
                identification = "" + ParsingUtils.getUInt16(subData);
                hex_identification = ParsingUtils.arrayToHexString(subData);

                reservd = (header.getByte(6) >>> 7) & 0x1;
                df = (header.getByte(6) >>> 6) & 0x1;
                mf = (header.getByte(6) >>> 5) & 0x1;
                hex_flags = ParsingUtils.byteToHex(header.getByte(6));

                flagsValue = "";

                if (reservd == 1) {
                    flagsValue = "Reserved ";
                }

                if (df == 1) {
                    flagsValue += "Don't fragment ";
                }

                if (mf == 1) {
                    flagsValue += "More fragments";
                }

                header.getBytes(6, subData);
                hex_fragmentOffset = ParsingUtils.arrayToHexString(subData);
                subData[0] = (byte) (subData[0] >>> 3);
                int offset = ParsingUtils.getUInt16(subData) & 0xff;
                fragmentOffset = "" + offset;

                ttl = "" + header.getUnsignedByte(8);
                hex_ttl = ParsingUtils.byteToHex(header.getByte(8));

                hex_protocol = ParsingUtils.byteToHex(header.getByte(9));

                header.getBytes(10, subData);
                checksum = "" + ParsingUtils.getUInt16(subData);
                hex_checksum = ParsingUtils.arrayToHexString(subData);

                subData = new byte[4];
                header.getBytes(12, subData);
                hex_source_ip = ParsingUtils.arrayToHexString(subData);

                header.getBytes(16, subData);
                hex_destination_ip = ParsingUtils.arrayToHexString(subData);
            } catch (Exception e) {
                logger.error("Can't parse header data. ", e);
            }
        }
    }

    @Override
    public Ip4 clone() {
        Ip4 cloned = new Ip4();
        cloned.header = header == null ? null : header.copy();
        cloned.protocol = protocol;
        cloned.protocolRaw = protocolRaw;
        cloned.length = length;
        cloned.totalLength = totalLength;
        cloned.sourceIpRaw = sourceIpRaw;
        cloned.destinationIpRaw = destinationIpRaw;
        cloned.intIdentification = intIdentification;
        cloned.intFragmentOffset = intFragmentOffset;
        cloned.tos = tos;
        cloned.identification = identification;
        cloned.fragmentOffset = fragmentOffset;
        cloned.ttl = ttl;
        cloned.checksum = checksum;
        cloned.flagsValue = flagsValue;
        cloned.version = version;
        cloned.ihl = ihl;
        cloned.reservd = reservd;
        cloned.df = df;
        cloned.mf = mf;
        cloned.hex_version = hex_version;
        cloned.hex_ihl = hex_ihl;
        cloned.hex_tos = hex_tos;
        cloned.hex_totalLength = hex_totalLength;
        cloned.hex_identification = hex_identification;
        cloned.hex_flags = hex_flags;
        cloned.hex_fragmentOffset = hex_fragmentOffset;
        cloned.hex_ttl = hex_ttl;
        cloned.hex_checksum = hex_checksum;
        cloned.hex_protocol = hex_protocol;
        cloned.hex_source_ip = hex_source_ip;
        cloned.hex_destination_ip = hex_destination_ip;
        return cloned;
    }

}
