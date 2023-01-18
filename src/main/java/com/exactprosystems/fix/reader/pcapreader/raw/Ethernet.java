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


public class Ethernet extends ProtocolHeader implements Cloneable {
    private static final Logger logger = LoggerFactory.getLogger(Ethernet.class);

    /**
     * Destination MAC 6 Bytes + Source MAC 6 Bytes + EtherType 2 Bytes (for Ethernet II packets)
     */
    public static final int ETHERNET_HEADER_SIZE = 14;
    public static final int VLAN_TAG_SIZE = 4;

    public ByteBuf data;
    public ByteBuf trailer;
    public String destination_mac, source_mac;
    public Protocol protocol;
    public int etherType;

    public void setDataAndProcess(ByteBuf _data) {
        data = _data;

        // https://en.wikipedia.org/wiki/EtherType
        etherType = data.getUnsignedShort(12);

        switch (etherType) {
            case 0x800:
                protocol = Protocol.IP4;
                break;
            case 0x8100: // most probably a 802.1Q tagged frame
            case 0x88a8: // 802.1ad, 802.1Q tagged frame follows
                protocol = Protocol._802_1Q;
                break;
            case 0x86dd:
                protocol = Protocol.IP6;
                break;
            default:
                protocol = null;
                break;
        }
    }

    @JsonIgnore
    public void setTrailer(ByteBuf _trailer) {
        trailer = _trailer;
    }

    @Override
    public void print() {
        logger.info("Ehernet II header type = {}", protocol != null ? protocol : etherType);
        logger.info("Data:");
        if (logger.isInfoEnabled()) {
            logger.info(ParsingUtils.byteBufToHex(data));
        }
    }

    @Override
    public void parseDetailedInfo() {
        if (data != null) {
            try {
                destination_mac = ParsingUtils.byteToHex(data.getByte(0));
                source_mac = ParsingUtils.byteToHex(data.getByte(6));

                for (int i = 1; i < 6; i++) {
                    destination_mac += ":" + ParsingUtils.byteToHex(data.getByte(i));
                    source_mac += ":" + ParsingUtils.byteToHex(data.getByte(i + 6));
                }
            } catch (Exception e) {
                logger.error("Can't parse header data.", e);
            }
        } else {
            destination_mac = source_mac = "";
        }
    }

    @Override
    public Ethernet clone() {
        Ethernet cloned = new Ethernet();
        cloned.data = data == null ? null : data.copy();
        cloned.trailer = trailer == null ? null : trailer.copy();
        cloned.destination_mac = destination_mac;
        cloned.source_mac = source_mac;
        cloned.protocol = protocol;
        cloned.etherType = etherType;
        return cloned;
    }
}
