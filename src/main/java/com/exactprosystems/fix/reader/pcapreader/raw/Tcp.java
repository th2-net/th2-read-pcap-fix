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
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// http://msdn.microsoft.com/en-us/library/ms819735.aspx TCP Keep-Alive Messages

public class Tcp extends ProtocolHeader implements Cloneable {
    private static final Logger logger = LoggerFactory.getLogger(Tcp.class);

    public ByteBuf header;

    public int source_port;
    public int destination_port;
    public int length;

    public boolean bServiseMsg = false;

    public boolean bAcknowledgment = false;
    public boolean bSynchronize = false;
    public boolean bReset = false;
    public boolean bFinal = false;

    public byte[] tcp_transact_id;

    // Detailed
    public String flags_value, hex_source_port, hex_destination_port, hex_sequence_number, hex_acknowledgment, hex_data_offset, hex_flags, hex_window_size, hex_checksum, hex_urgent_pointer, hex_optns, optns;
    public int data_offset, window_size, checksum, urgent_pointer;
    public long sequence_number, acknowledgment;
    public boolean cwr, ece, urg, ack, psh, rst, syn, fin;

    @JsonIgnore
    public void setHeader(ByteBuf data) {
        //http://en.wikipedia.org/wiki/Transmission_Control_Protocol
        header = data;
        length = data.readableBytes();

        // 000 Source port 16
        // 002 Destination port 16
        // 004 Sequence number 32
        // 008 Acknowledgment number 32
        // 012 Data offset 4 Reserved 4
        // 013 cwr/ece/urg/ack/psh/rsh/rst/syn/fin  8
        // 014 Window Size 16
        // 016 Checksum 16
        // 018 Urgent pointer 16
        // 020 .... Options (if Data Offset > 5)

        source_port = header.getUnsignedShort(0);
        destination_port = header.getUnsignedShort(2);

        sequence_number = header.getUnsignedInt(4);
        acknowledgment = header.getUnsignedInt(8);

        byte[] subData = new byte[8];
        header.getBytes(4, subData);
        tcp_transact_id = ParsingUtils.merge2ByteArrays(subData, new byte[]{header.getByte(13)});                // Used to determinate retransmissions

        byte flags = header.getByte(13);

        cwr = ((flags >>> 7) & 0x1) == 1 ? true : false;
        ece = ((flags >>> 6) & 0x1) == 1 ? true : false;
        urg = ((flags >>> 5) & 0x1) == 1 ? true : false;
        ack = ((flags >>> 4) & 0x1) == 1 ? true : false;
        psh = ((flags >>> 3) & 0x1) == 1 ? true : false;
        rst = ((flags >>> 2) & 0x1) == 1 ? true : false;
        syn = ((flags >>> 1) & 0x1) == 1 ? true : false;
        fin = (flags & 0x1) == 1 ? true : false;

        if (syn) {
            bSynchronize = true;
            bServiseMsg = true;
        } else if (rst) {
            bReset = true;
            bServiseMsg = true;
        } else if (fin) {
            bFinal = true;
            bServiseMsg = true;
        } else if (ack && !psh) {
            bAcknowledgment = true;
            bServiseMsg = true;
        }
    }

    @JsonIgnore
    public boolean isKeepAlive(long sequence) {
        return sequence - 1 == sequence_number;
    }

    @Override
    public void print() {
        logger.info("        TCP header: ");
        logger.info("            length: {}", length);
        logger.info("       source_port: {}", source_port);
        logger.info("  destination_port: {}", destination_port);
        logger.info("              Data: ");
        if (logger.isInfoEnabled()) {
            logger.info(ParsingUtils.byteBufToHex(header));
        }
    }

    @Override
    public Tcp clone() {
        Tcp cloned = new Tcp();
        cloned.header = header == null ? null : header.copy();
        cloned.source_port = source_port;
        cloned.destination_port = destination_port;
        cloned.length = length;
        cloned.bServiseMsg = bServiseMsg;
        cloned.bAcknowledgment = bAcknowledgment;
        cloned.bSynchronize = bSynchronize;
        cloned.bReset = bReset;
        cloned.bFinal = bFinal;
        cloned.tcp_transact_id = tcp_transact_id == null ? null : tcp_transact_id.clone();
        cloned.flags_value = flags_value;
        cloned.hex_source_port = hex_source_port;
        cloned.hex_destination_port = hex_destination_port;
        cloned.hex_sequence_number = hex_sequence_number;
        cloned.hex_acknowledgment = hex_acknowledgment;
        cloned.hex_data_offset = hex_data_offset;
        cloned.hex_flags = hex_flags;
        cloned.hex_window_size = hex_window_size;
        cloned.hex_checksum = hex_checksum;
        cloned.hex_urgent_pointer = hex_urgent_pointer;
        cloned.hex_optns = hex_optns;
        cloned.optns = optns;
        cloned.data_offset = data_offset;
        cloned.window_size = window_size;
        cloned.checksum = checksum;
        cloned.urgent_pointer = urgent_pointer;
        cloned.sequence_number = sequence_number;
        cloned.acknowledgment = acknowledgment;
        cloned.cwr = cwr;
        cloned.ece = ece;
        cloned.urg = urg;
        cloned.ack = ack;
        cloned.psh = psh;
        cloned.rst = rst;
        cloned.syn = syn;
        cloned.fin = fin;
        return cloned;
    }

    @Override
    @Deprecated
    public void parseDetailedInfo() {
        //FixMe: use ByteBuf instead of byte[]
    }


}
