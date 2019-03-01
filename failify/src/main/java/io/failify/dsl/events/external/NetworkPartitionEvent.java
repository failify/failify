/*
 * MIT License
 *
 * Copyright (c) 2017-2019 Armin Balalaie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.failify.dsl.events.external;

import io.failify.dsl.entities.Deployment;
import io.failify.execution.LimitedRuntimeEngine;
import io.failify.dsl.events.ExternalEvent;
import io.failify.execution.NetPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an external event to impose or remove a network partition
 */
public class NetworkPartitionEvent extends ExternalEvent {
    private final static Logger logger = LoggerFactory.getLogger(NetworkPartitionEvent.class);

    private final NetPart netPart; // the partitions to use for network partition
    private final boolean removePartition; // the flag to mark the event as partition removal

    private NetworkPartitionEvent(Builder builder) {
        super(builder.getName());
        netPart = builder.netPart;
        removePartition = builder.removePartition;
    }

    @Override
    protected void execute(LimitedRuntimeEngine runtimeEngine) throws Exception {
        if (removePartition) {
            runtimeEngine.removeNetworkPartition(netPart);
        } else {
            runtimeEngine.networkPartition(netPart);
        }
    }

    /**
     * The builder class for building a network partition event
     */
    public static class Builder extends BuilderBase<NetworkPartitionEvent, Deployment.Builder> {
        private NetPart netPart;
        private boolean removePartition;

        public Builder(Deployment.Builder parentBuilder, String name) {
        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param name the name of the network partition event to be built
         */
            super(parentBuilder, name);
            netPart = null;
        }

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param instance a network partition event object instance to be changed
         */
        public Builder(Deployment.Builder parentBuilder, NetworkPartitionEvent instance) {
            super(parentBuilder, instance);
            netPart = instance.netPart;
            removePartition = new Boolean(instance.removePartition);
        }

        /**
         * Sets the partitions to use for network partition
         * @param netPart the partitions to use for network partition
         * @return the current builder instance
         */
        public Builder scheme(NetPart netPart) {
            this.netPart = netPart;
            return this;
        }

        /**
         * Marks the event as partition removal event
         * @return the current builder instance
         */
        public Builder removePartition() {
            this.removePartition = true;
            return this;
        }

        @Override
        public NetworkPartitionEvent build() {
            return new NetworkPartitionEvent(this);
        }

        @Override
        protected void returnToParent(NetworkPartitionEvent builtObj) {
            parentBuilder.networkPartitionEvent(builtObj);
        }
    }
}
