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

package com.exactprosystems.fix.reader.prometheus;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

import static com.exactpro.th2.common.metrics.CommonMetrics.DIRECTION_LABEL;
import static com.exactpro.th2.common.metrics.CommonMetrics.SESSION_ALIAS_LABEL;

public class PrometheusMetrics {
    private PrometheusMetrics() {
    }

    public static final Counter GAP_NUMBER = Counter.build()
            .name("th2_pcap_read_tcp_gaps")
            .labelNames("protocol")
            .help("Number of lost packets")
            .register();

    public static final Counter READ_MESSAGES_NUMBER = Counter.build()
            .name("pcap_read_messages_number")
            .labelNames("protocol")
            .help("Number of read messages by protocol")
            .register();

    public static final Counter SKIPPED_PACKETS_NUMBER = Counter.build()
            .name("th2_pcap_read_skipped_packets_number")
            .help("Number of skipped packets")
            .register();

    public static final Counter SENT_MESSAGES_NUMBER = Counter.build()
            .name("th2_pcap_read_sent_messages")
            .help("Number of sent messages")
            .labelNames(SESSION_ALIAS_LABEL, DIRECTION_LABEL)
            .register();

    public static final Counter SENT_MESSAGES_SIZE = Counter.build()
            .name("th2_pcap_read_sent_messages_size")
            .help("Size of sent messages")
            .labelNames(SESSION_ALIAS_LABEL, DIRECTION_LABEL)
            .register();

    public static final Gauge LAST_MESSAGE_TIMESTAMP = Gauge.build()
            .name("th2_read_published_message_timeout_milliseconds")
            .help("contains the timestamp of the last processed message in milliseconds for corresponding alias and direction")
            .labelNames(SESSION_ALIAS_LABEL, DIRECTION_LABEL)
            .register();

    public static final Counter READ_FILE_SIZE = Counter.build()
            .name("pcap_read_file_size")
            .labelNames("file_name")
            .help("Size of processed file (in bytes)")
            .register();
}
