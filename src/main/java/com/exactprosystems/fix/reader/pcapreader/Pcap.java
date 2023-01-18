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

import com.exactprosystems.fix.reader.pcapreader.constants.PcapFormat;
import com.exactprosystems.fix.reader.pcapreader.constants.Protocol;
import com.exactprosystems.fix.reader.pcapreader.raw.Ethernet;
import com.exactprosystems.fix.reader.pcapreader.raw.Ip4;
import com.exactprosystems.fix.reader.pcapreader.raw.Ip6;
import com.exactprosystems.fix.reader.pcapreader.raw.Tcp;
import com.exactprosystems.fix.reader.pcapreader.state.DefaultInstantDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static com.exactprosystems.fix.reader.pcapreader.ParsingUtils.byteBufToHex;
import static com.exactprosystems.fix.reader.pcapreader.ScheduledFile.DEFAULT_CAPTURE_DAY;
import static com.exactprosystems.fix.reader.pcapreader.raw.Ethernet.ETHERNET_HEADER_SIZE;
import static com.exactprosystems.fix.reader.pcapreader.raw.Ethernet.VLAN_TAG_SIZE;

public class Pcap implements Cloneable {
    private static final Logger logger = LoggerFactory.getLogger(Pcap.class);
    private static final Set<Protocol> UNSUPPORTED_PROTOCOLS = EnumSet.of(Protocol.IP6);

    public final int captureDay;
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = DefaultInstantDeserializer.class)
    public Instant timestamp;
    public long incl_len, orig_len;
    public ByteBuf header, data, payload, notParsedData;
    public ByteBuf effectivePayload; // Payload which constitutes to socket level protocol, i.e TCP
    public String captureFileId, status, RawUser, source, beforeGapPacket;
    public long packetNum;
    public int headerLength, dataLength, payloadLength;

    private int ip4_offset, subHeaders_length, offset;

    public Ethernet header_ethernet2 = null;
    public Ip4 header_ip4 = null;
    public Ip6 header_ip6 = null;
    public Tcp header_tcp = null;

    boolean header_unknown = false;

    // TODO It should go to reader
    public void setHeader(String _captureFileId, long _packetNum, ByteBuf _header, PcapFormat pcapFormat) {
        captureFileId = _captureFileId;
        packetNum = _packetNum;
        header = _header;
        headerLength = header.readableBytes();

		/* typedef struct pcaprec_hdr_s {
		   24  guint32 ts_sec;         // timestamp seconds
		   28  guint32 ts_usec;        // timestamp microseconds
		   32  guint32 incl_len;       // number of octets of packet saved in file
		   36  guint32 orig_len;       // actual length of packet
			} pcaprec_hdr_t; */

        if (_header != null && _header.readableBytes() >= 16) {
            long ts_sec = header.getUnsignedIntLE(0);
            long ts_usec = header.getUnsignedIntLE(4);
            timestamp = PcapReader.getRecordTimestamp(ts_sec, ts_usec, pcapFormat);
            incl_len = header.getUnsignedIntLE(8);
            orig_len = header.getUnsignedIntLE(12);
        }
    }

    public Pcap() {
        captureDay = DEFAULT_CAPTURE_DAY;
    }

    public Pcap(int captureDay) {
        this.captureDay = captureDay;
    }

    public void setData(ByteBuf _data) {
        data = _data;
        dataLength = data.readableBytes();
    }

    public void parseHeader() {
        try {
            int length = data.readableBytes();

            // 14 bytes Ethernet_II
            if (length >= 14) {
                header_ethernet2 = new Ethernet(); // FIXME code smell. Nobody fills in that one.

                Protocol subProtocol;

                header_ethernet2.setDataAndProcess(data.slice(data.readerIndex() + offset, ETHERNET_HEADER_SIZE));

                if (logger.isTraceEnabled()) {
                    logger.trace("Ethernet II Header: {}", ParsingUtils.byteBufToHexString(header_ethernet2.data));
                }

                offset = Ethernet.ETHERNET_HEADER_SIZE;
                subProtocol = header_ethernet2.protocol;

                boolean bIP4 = false;

                if (subProtocol == Protocol._802_1Q) {
                    if (header_ethernet2.etherType == 0x8100) {
                        // we do not handle QinQ a.t.m. (and probably never will)
                        // to do so we have to check the "new" EtherType
                    } else { // we should check for etherType "88 a8" here, but let's play simple a.t.m.
                        // in that case 802.1Q tag immediately follows the very similar 802.1ad tag, the same 4 bytes
                        offset += VLAN_TAG_SIZE;
                    }

                    offset += VLAN_TAG_SIZE;
                } else if (subProtocol == Protocol.IP6) {
                    header_ip6 = new Ip6();
                }

                if (subProtocol == Protocol.IP4 || bIP4)        // ip4 header
                {
                    if (length >= 34)            // 20 bytes ip4 min header length + 14 bytes Ethernet II header
                    {
                        header_ip4 = new Ip4();

                        int IHL = data.getByte(data.readerIndex() + offset) & 0xf;                                                // get low 4 bits
                        int payloadStart = IHL * 4;                                                    // 32 * IHL /8 = XX bytes
                        header_ip4.setHeader(data.slice(data.readerIndex() + offset, payloadStart));

                        if (logger.isTraceEnabled()) {
                            logger.trace("IPv4 Header: [offset: {}, length: {}], bytes: {}", offset, header_ip4.length, ParsingUtils.byteBufToHexString(header_ip4.header));
                        }

                        ip4_offset = offset;
                        subHeaders_length = header_ip4.length;

                        offset += header_ip4.length;

                        if (header_ip4.protocol == Protocol.TCP) {
                            if (length >= 54) {
                                header_tcp = new Tcp();
                                int dataOffset = (data.getByte(data.readerIndex() + offset + 12) >>> 4) & 0xf; // get high 4 bits
                                header_tcp.setHeader(data.slice(data.readerIndex() + offset, dataOffset * 4)); // dataOfset in 32 bit words so dataOffset * 32 /8 = dataOffset*4

                                if (logger.isTraceEnabled()) {
                                    logger.trace("TCP Header: [offset: {}, length: {}], bytes: {}", offset, header_tcp.length, ParsingUtils.byteBufToHexString(header_tcp.header));
                                }

                                offset += dataOffset * 4;
                                subHeaders_length += dataOffset * 4;
                            } else {
                                header_unknown = true;
                            }
                        } else if (header_ip4.protocol == Protocol.ICMP || header_ip4.protocol == Protocol.IGMP) {
                            header_unknown = false;
                        } else {
                            logger.error("Unknown protocol: {}", header_ip4.protocol);
                            header_unknown = true;
                        }
                    } else {
                        header_unknown = true;
                        notParsedData = data.slice(data.readerIndex() + offset, length - offset);
                    }
                } else if (UNSUPPORTED_PROTOCOLS.contains(subProtocol)) {
                    header_unknown = false;
                } else {
                    logger.error("Ip4 header not found. Protocol: {} packet num = {}", subProtocol, packetNum);
                    header_unknown = true;

                    notParsedData = data.slice(data.readerIndex() + offset, length - offset);
                }
            }
        } catch (Exception e) {
            logger.error("Pcap.parse() error", e);
        }
    }

    public void parseData() {
        int length = data.readableBytes();

        if (header_ip4 == null) {
            return;
        }

        int sub_offset = ip4_offset + subHeaders_length;
        int sub_length = header_ip4.totalLength - subHeaders_length;

        if (sub_offset + sub_length >= length) {
            sub_length = length - sub_offset;
        }
        payload = data.copy(data.readerIndex() + sub_offset, sub_length);
        payloadLength = payload.readableBytes();
        if (logger.isTraceEnabled()) {
            logger.trace("Payload: [offset: {}, length: {}], bytes: {}", offset, payload.readableBytes(), ParsingUtils.byteBufToHex(payload));
        }

        if (header_ip4.totalLength - subHeaders_length != length - offset) {
            ByteBuf trailer = data.slice(data.readerIndex() + ip4_offset + header_ip4.totalLength, length - ip4_offset - header_ip4.totalLength);

            if (logger.isTraceEnabled()) {
                logger.trace("Trailer: [offset: {}, length: {}], bytes: {}", ip4_offset + header_ip4.totalLength, length - ip4_offset - header_ip4.totalLength, ParsingUtils.byteBufToHexString(trailer));
            }

            if (header_ethernet2 != null) {
                header_ethernet2.setTrailer(trailer);
            }
        }
    }

    public TcpIPId getTCPIPId(boolean reverted) {
        if (header_ip4 != null && header_tcp != null) {
            if (!reverted) {
                return new TcpIPId(header_ip4.sourceIpRaw, header_tcp.source_port, header_ip4.destinationIpRaw, header_tcp.destination_port);
            } else {
                return new TcpIPId(header_ip4.destinationIpRaw, header_tcp.destination_port, header_ip4.sourceIpRaw, header_tcp.source_port);
            }
        } else {
            return null;
        }
    }

    @JsonIgnore
    public boolean needResetSequence() {
        return header_tcp != null && (header_tcp.rst || header_tcp.syn || header_tcp.fin);
    }

    @JsonIgnore
    public long getSequence() {
        return header_tcp != null ? header_tcp.sequence_number : -1;
    }

    @JsonIgnore
    public long getAck() {
        return header_tcp != null ? header_tcp.acknowledgment : -1;
    }

    @JsonIgnore
    public PacketTransactSequence getTransactSequence() {
        if (header_tcp != null) {
            int payloadSize = payload == null ? 0 : payload.readableBytes();
            return new PacketTransactSequence(payloadSize, header_tcp.sequence_number, header_tcp.acknowledgment);
        } else {
            return null;
        }
    }

    @JsonIgnore
    public boolean isEthernetHeaderExist() {
        return header_ethernet2 != null;
    }

    @JsonIgnore
    public boolean isTCPHeaderExist() {
        return header_tcp != null;
    }

    //used only for state saving
    @Override
    public Pcap clone() {
        Pcap cloned = new Pcap(captureDay);
        cloned.timestamp = timestamp;
        cloned.incl_len = incl_len;
        cloned.orig_len = orig_len;
        cloned.header = header == null ? null : header.copy();
        cloned.data = data == null ? null : data.copy();
        cloned.payload = payload == null ? null : payload.copy();
        cloned.notParsedData = notParsedData == null ? null : notParsedData.copy();
        cloned.effectivePayload = effectivePayload == null ? null : effectivePayload.copy();
        cloned.captureFileId = captureFileId;
        cloned.status = status;
        cloned.RawUser = RawUser;
        cloned.source = source;
        cloned.beforeGapPacket = beforeGapPacket;
        cloned.packetNum = packetNum;
        cloned.headerLength = headerLength;
        cloned.dataLength = dataLength;
        cloned.payloadLength = payloadLength;
        cloned.header_unknown = header_unknown;
        cloned.header_ethernet2 = header_ethernet2 == null ? null : header_ethernet2.clone();
        cloned.header_ip4 = header_ip4 == null ? null : header_ip4.clone();
        cloned.header_ip6 = header_ip6 == null ? null : header_ip6.clone();
        cloned.header_tcp = header_tcp == null ? null : header_tcp.clone();
        return cloned;
    }

    public void print() {
        logger.debug("[packet starts]==========================================================================================");
        logger.debug(" timestamp = {} incl_len = {} orig_len = {}", timestamp, incl_len, orig_len);

        if (logger.isDebugEnabled()) {
            logger.debug(" HEX header:");
            logger.debug("{}\r\n", byteBufToHex(header));

            logger.debug(" HEX data:");
            logger.debug("{}\r\n", byteBufToHex(data));
        }

        if (header_ethernet2 != null) {
            header_ethernet2.print();
        } else {
            logger.debug("Ethernet II header not found");
        }

        if (header_ip4 != null) {
            header_ip4.print();
        } else {
            logger.debug("IPv4 header not found");
        }

        if (header_tcp != null) {
            header_tcp.print();
        } else {
            logger.debug("TCP header not found");
        }

        if (payload != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Payload: ");
                logger.debug(byteBufToHex(payload));
            }
        } else {
            logger.debug("Payload not found");
        }

        logger.debug("==========================================================================================[packet ends  ]");
    }
}
