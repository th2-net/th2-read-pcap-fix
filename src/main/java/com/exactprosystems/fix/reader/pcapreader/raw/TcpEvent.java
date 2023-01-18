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

public class TcpEvent {
    public static final String TCP_OK = "";

    public static final String TCP_LOST_PACKET = "TCP lost segment";
    public static final String TCP_LOST_PACKET_FOUND = "TCP segment restored";
    public static final String TCP_RETRANSMISSION = "TCP retransmission";
    public static final String TCP_OUT_OF_ORDER = "TCP out-of-order";
    public static final String TCP_OUT_OF_ORDER_FOUND = "TCP out-of-order found";
    public static final String TCP_DUPLICATE_ACK = "TCP Dup Ack";
    public static final String TCP_KEEP_ALIVE = "TCP Keep-Alive";

    private TcpEvent() {
    }
}
