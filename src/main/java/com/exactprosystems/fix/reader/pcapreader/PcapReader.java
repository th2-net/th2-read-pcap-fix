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

import com.exactprosystems.fix.reader.prometheus.PrometheusMetrics;
import com.exactprosystems.fix.reader.pcapreader.constants.PcapFormat;
import com.exactprosystems.fix.reader.pcapreader.constants.PcapTimestampResolution;
import com.exactprosystems.fix.reader.pcapreader.constants.UnpackOption;
import com.exactprosystems.fix.reader.pcapreader.raw.TcpEvent;
import com.exactprosystems.fix.reader.pcapreader.tcpstream.TcpStreamFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.exactprosystems.fix.reader.pcapreader.constants.Constants.MICRO_SCALE;
import static com.exactprosystems.fix.reader.pcapreader.constants.Constants.NANO_SCALE;

public class PcapReader implements AutoCloseable {
    // http://wiki.wireshark.org/Development/LibpcapFileFormat
    // http://wiki.wireshark.org/TCP_Analyze_Sequence_Numbers
    // http://www.tcpipguide.com/free/t_TCPSegmentRetransmissionTimersandtheRetransmission.htm

    private static final Logger logger = LoggerFactory.getLogger(PcapReader.class);
    public static final int PCAP_GLOBAL_HEADER_LENGTH = 24;
    private static final int PCAP_RECORD_HEADER_LENGTH = 16;
    private static final int PCAP_SUBPROTOCOL_HEADER_LENGTH = 66;

    private String _fileID = "";
    private PcapFormat pcapFormat = PcapFormat.TCPDUMP;
    private RecordReader reader = null;
    private long packetsRead;

    private final Map<TcpIPId, List<PacketTransactSequence>> _transSequences;
    private final Map<TcpIPId, Long> _lastSequence;
    private final Map<TcpIPId, Long> _lastAck;
    private final Map<TcpIPId, Long> _lastPayloadLength;
    private final TcpStreamFactory tcpStreamFactory;
    protected UnpackOption unpackOption;
    private int captureDay;

    private ByteBuf buffer;

    public PcapReader(UnpackOption unpackOption, TcpStreamFactory tcpStreamFactory) {
        this.unpackOption = unpackOption;
        _transSequences = new HashMap<>();
        _lastSequence = new HashMap<>();
        _lastAck = new HashMap<>();
        _lastPayloadLength = new HashMap<>();
        buffer = Unpooled.buffer();
        this.tcpStreamFactory = tcpStreamFactory;
    }

    public PcapReader(TcpStreamFactory tcpStreamFactory) {
        this(UnpackOption.AUTO, tcpStreamFactory);
    }

    /**
     * @param inputStream stream to read, usually corresponds to a file on a local or remote filesystem<br>
     *                    after return {@code true} the Reader's stream is positioned on the start of the 1st packet (record)
     * @param sourceId    TODO ???
     * @param captureDay  the day this file belongs to, usually determined from the 1st packet read beforehand
     * @return {@code true} when file was found to be a PCAP of known format/timestamp resolution<br>
     * {@code false} when file type is unknown or if its stream has less then {@value #PCAP_GLOBAL_HEADER_LENGTH} bytes
     * @throws IOException when RecordReader can't read all the needed bytes
     */
    public boolean openOffline(InputStream inputStream, String sourceId, int captureDay, long tcpdumpSnapshotLength) throws IOException {
        _fileID = sourceId;
        packetsRead = 0;
        this.captureDay = captureDay;
        if (reader != null) {
            reader.close();
        }
        InputStream is = Unpack.getInputStream(inputStream, unpackOption);
        reader = new RecordReader(is, tcpdumpSnapshotLength);
        return readPcapFileHeader();
    }

    private static byte[] findMagicNumber(ByteBuf bytes) {
        if (bytes == null || bytes.readableBytes() < 4) {
            return new byte[0];
        }

        byte[] result = new byte[4];
        bytes.readBytes(result);

        return result;
    }

    /**
     * Identify PCAP file (stream) timestamp resolution
     *
     * @return {@code true} when all of PCAP Global Header read and interpreted OK<br>
     * {@code false} otherwise, i.e. stream has less than {@value #PCAP_GLOBAL_HEADER_LENGTH} bytes,<br>
     * or if they are not recognized as a valid header
     * @throws IOException if something unexpected happens while reading those {@value #PCAP_GLOBAL_HEADER_LENGTH} bytes
     */
    private boolean readPcapFileHeader() throws IOException {
        try {
            if (!reader.fill(buffer, PCAP_GLOBAL_HEADER_LENGTH)) { // file has 0 bytes
                return false;
            }
        } catch (EOFException e) { // most probably, stream has less than required number of bytes
            logger.error("Can't read PCAP global header", e);
            return false;
        }

        byte[] magicNumberBytes = findMagicNumber(buffer);
        PcapTimestampResolution timestampResolution = MagicNumbers.identifyPcapTimestampResolution(magicNumberBytes);
        if (PcapTimestampResolution.MICROSECOND_RESOLUTION.equals(timestampResolution)) {
            pcapFormat = PcapFormat.TCPDUMP;
        } else if (PcapTimestampResolution.NANOSECOND_RESOLUTION.equals(timestampResolution)) {
            pcapFormat = PcapFormat.NANOSECONDLIBPCAP;
        } else {
            logger.error("PCAP stream starts with invalid magic number");
            return false;
        }
        return true;
    }

    public PcapFormat getPcapFormat() {
        return pcapFormat;
    }

	/* typedef struct pcap_hdr_s {
    0  guint32 magic_number;   // magic number
    4  guint16 version_major;  // major version number
    6  guint16 version_minor;  // minor version number
    8  gint32  thiszone;       // GMT to local correction
   12  guint32 sigfigs;        // accuracy of timestamps
   16  guint32 snaplen;        // max length of captured packets, in octets
   20  guint32 network;        // data link type
		} pcap_hdr_t;*/

	/* typedef struct pcaprec_hdr_s {
   24  guint32 ts_sec;         // timestamp seconds
   28  guint32 ts_usec;        // timestamp microseconds
   32  guint32 incl_len;       // number of octets of packet saved in file
   36  guint32 orig_len;       // actual length of packet
	} pcaprec_hdr_t;*/

    /**
     * Return the next packet (record) from this Reader's stream<br>
     *
     * @return parsed Pcap, where TCP/UDP fields like IP address, or port can be retrieved if found
     * @throws IOException if a packet cannot be read fully
     */
    public Pcap readPacket(boolean skip) throws IOException {
        buffer.clear();
        long todoPacket = packetsRead + 1;
        Pcap packet = new Pcap(captureDay);
        try {
            if (!reader.fill(buffer, PCAP_RECORD_HEADER_LENGTH)) { // stream is at EOF, no packets left
                return null;
            }
        } catch (IOException e) {
            throw new IOException(String.format("Error reading packet #%d header", todoPacket), e);
        }

        ByteBuf header = buffer.readSlice(PCAP_RECORD_HEADER_LENGTH);
        if (logger.isTraceEnabled()) logger.trace("Packet header: {}", ParsingUtils.byteBufToHexString(header));

        packet.setHeader(_fileID, todoPacket, header, pcapFormat);

        int payloadSize = (int) packet.incl_len; // incl_len is uInt32 per PCAP spec
        if (payloadSize <= 0) {
            String errorText = payloadSize == 0 ?
                    "Packet data size 0 makes no sense" :
                    String.format("Cannot handle packet data size  > %d", Integer.MAX_VALUE);
            logger.error(errorText);
            packet.status = errorText;
            return packet;
        }

        logger.trace("Reading packet data");
        try {
            if (!reader.fill(buffer, Math.min(PCAP_SUBPROTOCOL_HEADER_LENGTH, payloadSize))) { // stream is at EOF after reading packet header
                throw new IOException(String.format("PCAP stream ended after packet header. Can read none of %d bytes of packet data", payloadSize));
            }
        } catch (IOException e) {
            throw new IOException(String.format("Can't get PCAP packet data for packet #%d, maybe file is truncated?", todoPacket), e);
        }
        packetsRead++;
        if (skip) {
            reader.skip(Math.max(payloadSize - PCAP_SUBPROTOCOL_HEADER_LENGTH, 0));
            return packet;
        }
        packet.setData(buffer);

        logger.debug("Parsing PCAP header");
        packet.parseHeader();

        if (packet.header_tcp != null) {
            if (tcpStreamFactory
                    .getTcpStreamForAddress(packet.header_ip4.sourceIpRaw,
                            packet.header_tcp.source_port,
                            packet.header_ip4.destinationIpRaw,
                            packet.header_tcp.destination_port) == null) {
                PrometheusMetrics.SKIPPED_PACKETS_NUMBER.inc();
                reader.skip(Math.max(payloadSize - PCAP_SUBPROTOCOL_HEADER_LENGTH, 0));
                return packet;
            }
        }

        if (PCAP_SUBPROTOCOL_HEADER_LENGTH < payloadSize) {
            logger.trace("Reading packet data");
            try {
                if (!reader.fill(buffer, payloadSize - PCAP_SUBPROTOCOL_HEADER_LENGTH)) { // stream is at EOF after reading packet header
                    throw new IOException(String.format("PCAP stream ended after packet header. Can read none of %d bytes of packet data", payloadSize));
                }
            } catch (IOException e) {
                throw new IOException(String.format("Can't get PCAP packet data for packet #%d, maybe file is truncated?", todoPacket), e);
            }

            packet.setData(buffer);
        }
        logger.debug("Parsing PCAP data");
        packet.parseData();


        PacketTransactSequence pSeq = packet.getTransactSequence();

        if (pSeq != null) {
            TcpIPId cliID = packet.getTCPIPId(false);
            TcpIPId revCliID = packet.getTCPIPId(true);

            List<PacketTransactSequence> cliSeq = _transSequences.get(cliID);

            long payloadLength = packet.payload != null ? packet.payload.readableBytes() : 0;

            if (logger.isDebugEnabled()) {
                logger.debug("Conversation: {}: seq {}, ack {}, length {}", cliID.toString().replace("_", " > "), packet.getSequence(), packet.getAck(), payloadLength);
            }

            if (cliSeq == null) {
                List<PacketTransactSequence> cliSeqs = new ArrayList<>();
                cliSeqs.add(pSeq);
                _transSequences.put(cliID, cliSeqs);
                packet.status = setStateBySequence(cliID, revCliID, packet);
            } else {
                if (packet.needResetSequence()) {
                    cliSeq.clear();

                    _lastSequence.put(cliID, 0L);
                    _lastSequence.put(revCliID, 0L);

                    _lastAck.put(cliID, 0L);
                    _lastAck.put(revCliID, 0L);
                }

                if (cliSeq.contains(pSeq)) {
                    if (packet.header_tcp.header.getByte(13) == (byte) 16 && (packet.payload == null || packet.payload.readableBytes() == 0)) {
                        packet.status = "TCP Dup Ack";
                    } else {
                        packet.status = "TCP retransmission";
                    }
                } else {
                    packet.status = setStateBySequence(cliID, revCliID, packet);
                    cliSeq.add(pSeq);
                    if (cliSeq.size() > 100) {
                        cliSeq.remove(0);
                    }
                }
            }
            logger.debug("Packet status: {}", packet.status);
            if (packet.status.equals("") || packet.status.equals("TCP lost segment")) {
                _lastPayloadLength.put(cliID, payloadLength);
            }
        }
        logger.debug("Finished reading the packet: #{}", packetsRead);
        return packet;
    }

    // ts_sec and ts_usec should be valid positive numbers. No additional checks are made.
    public static Instant getRecordTimestamp(long seconds, long microOrNanoSeconds, PcapFormat format) {
        long nanoSeconds = (format == PcapFormat.NANOSECONDLIBPCAP)
                ? microOrNanoSeconds
                : microOrNanoSeconds * (NANO_SCALE / MICRO_SCALE);
        return Instant.ofEpochSecond(seconds, nanoSeconds);
    }

    private String setStateBySequence(TcpIPId cliID, TcpIPId revCliID, Pcap packet) {
        long sequence = packet.getSequence();
        long acknowledgment = packet.getAck();
        boolean isSyn = packet.header_tcp.syn;

        logger.trace("Analysing Seq/Ack numbers");
        long seq = _lastSequence.containsKey(cliID) ? _lastSequence.get(cliID) : 0;
        long ack = _lastAck.containsKey(revCliID) ? _lastAck.get(revCliID) : 0;

        if (seq == 0 && ack == 0) {
            logger.trace("First packet in the conversation");
            _lastSequence.put(cliID, isSyn ? sequence + 1 : sequence);
            _lastAck.put(cliID, acknowledgment);
            return TcpEvent.TCP_OK;
        }

        if (seq != 0 && _lastPayloadLength.containsKey(cliID)) {
            long payloadLength = _lastPayloadLength.get(cliID);
            if (seq + payloadLength == sequence) {
                _lastSequence.put(cliID, sequence);
                _lastAck.put(cliID, acknowledgment);
                return TcpEvent.TCP_OK;
            } else if (seq + payloadLength >= 0xffffffffL && (0xffffffffL - seq + sequence + 1 - payloadLength) == 0L) {
                _lastSequence.put(cliID, sequence);
                _lastAck.put(cliID, acknowledgment);
                return TcpEvent.TCP_OK;
            } else if (sequence > seq + payloadLength ||
                    sequence > (seq + payloadLength) % 0xFFFFFFFFL) {
                // current seq greater than the next expected seq for previous packet in the same direction
                _lastSequence.put(cliID, sequence);
                _lastAck.put(cliID, acknowledgment);
                return TcpEvent.TCP_LOST_PACKET;
            }
        }

        long diff = seq >= ack ? sequence - seq : sequence - ack;

        if (diff == -1) {
            return TcpEvent.TCP_KEEP_ALIVE;
        } else if (diff < -1) {
            return TcpEvent.TCP_OUT_OF_ORDER;
        } else {
            _lastSequence.put(cliID, sequence);
            _lastAck.put(cliID, acknowledgment);

            if (diff > 1) {
                if (_lastAck.containsKey(revCliID) && _lastAck.get(revCliID) == sequence) {
                    return TcpEvent.TCP_OK;
                } else if (_lastSequence.containsKey(cliID) && _lastSequence.get(cliID) == sequence) {
                    return TcpEvent.TCP_OK;
                } else {
                    return TcpEvent.TCP_LOST_PACKET;
                }
            } else {
                return TcpEvent.TCP_OK;
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
            reader = null;
        }
    }
}
