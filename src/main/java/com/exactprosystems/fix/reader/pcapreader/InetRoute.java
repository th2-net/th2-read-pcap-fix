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

import java.util.Objects;

public class InetRoute {
    private final InetSocketAddress sourceAddress;

    private final InetSocketAddress destinationAddress;

    private InetRoute(InetSocketAddress sourceAddress, InetSocketAddress destinationAddress) {
        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;
    }

    public static InetRoute of(InetSocketAddress sourceAddress, InetSocketAddress targetAddress) {
        if ((sourceAddress == null) || (targetAddress == null)) {
            throw new NullPointerException();
        }
        return new InetRoute(sourceAddress, targetAddress);
    }

    public InetSocketAddress getSourceAddress() {
        return sourceAddress;
    }

    public InetSocketAddress getDestinationAddress() {
        return destinationAddress;
    }

    public InetRoute swap() {
        return new InetRoute(destinationAddress, sourceAddress);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InetRoute inetRoute = (InetRoute) o;
        return sourceAddress.equals(inetRoute.sourceAddress) &&
                destinationAddress.equals(inetRoute.destinationAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceAddress, destinationAddress);
    }

    @Override
    public String toString() {
        return "{" + sourceAddress + " -> " + destinationAddress + '}';
    }
}
