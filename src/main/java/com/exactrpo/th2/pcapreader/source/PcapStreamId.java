/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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

package com.exactrpo.th2.pcapreader.source;

import java.util.Objects;
import java.util.StringJoiner;

import com.exactrpo.th2.pcapreader.util.ParameterUtil;

public class PcapStreamId {
    private final Address src;
    private final Address dst;

    public PcapStreamId(Address src, Address dst) {
        this.src = Objects.requireNonNull(src, "src");
        this.dst = Objects.requireNonNull(dst, "dst");
    }

    public Address getSrc() {
        return src;
    }

    public Address getDst() {
        return dst;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PcapStreamId that = (PcapStreamId)o;
        return src.equals(that.src) && dst.equals(that.dst);
    }

    @Override
    public int hashCode() {
        return Objects.hash(src, dst);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", PcapStreamId.class.getSimpleName() + "[", "]")
                .add("src=" + src)
                .add("dst=" + dst)
                .toString();
    }

    public static class Address {
        private final int ip;
        private final int port;

        public Address(int ip, int port) {
            ParameterUtil.checkPositive(ip, "ip");
            ParameterUtil.checkPositive(port, "port");
            this.ip = ip;
            this.port = port;
        }

        public int getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Address address = (Address)o;
            return ip == address.ip && port == address.port;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ip, port);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Address.class.getSimpleName() + "[", "]")
                    .add("ip=" + ip)
                    .add("port=" + port)
                    .toString();
        }
    }
}
