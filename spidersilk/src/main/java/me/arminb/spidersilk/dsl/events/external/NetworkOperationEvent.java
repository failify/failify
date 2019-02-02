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

package me.arminb.spidersilk.dsl.events.external;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.events.ExternalEvent;
import me.arminb.spidersilk.execution.LimitedRuntimeEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an external event to execute a network operation including link failure and network partition
 */
public class NetworkOperationEvent extends ExternalEvent {
    private final static Logger logger = LoggerFactory.getLogger(NetworkOperationEvent.class);

    private final String nodePartitions; // the scheme of node partitions to use for operation
    private final NetworkOperation networkOperation; // the type of the network operation

    private NetworkOperationEvent(Builder builder) {
        super(builder.getName());
        nodePartitions = builder.nodePartitions;
        networkOperation = builder.networkOperation;
    }

    @Override
    protected void execute(LimitedRuntimeEngine runtimeEngine) throws Exception {
        switch (networkOperation) {
            case PARTITION:
                runtimeEngine.networkPartition(nodePartitions); break;
            case REMOVE_PARTITION:
                runtimeEngine.removeNetworkPartition(); break;
            case LINK_DOWN:
                runtimeEngine.linkDown(nodePartitions.split(",")[0].trim(), nodePartitions.split(",")[1].trim()); break;
            case LINK_UP:
                runtimeEngine.linkUp(nodePartitions.split(",")[0].trim(), nodePartitions.split(",")[1].trim()); break;
        }
    }

    /**
     * The builder class for building a network operation event
     */
    public static class Builder extends BuilderBase<NetworkOperationEvent, Deployment.Builder> {
        private String nodePartitions;
        private NetworkOperation networkOperation;

        public Builder(Deployment.Builder parentBuilder, String name) {
        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param name the name of the network operation event to be built
         */
            super(parentBuilder, name);
            nodePartitions = null;
        }

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param instance a network operation event object instance to be changed
         */
        public Builder(Deployment.Builder parentBuilder, NetworkOperationEvent instance) {
            super(parentBuilder, instance);
            nodePartitions = new String(instance.nodePartitions);
            networkOperation = instance.networkOperation;
        }

        /**
         * Sets the scheme of node partitions to use for operation
         * @param nodePartitions the scheme of node partitions to use for operation
         * @return the current builder instance
         */
        public Builder nodePartitions(String nodePartitions) {
            this.nodePartitions = nodePartitions;
            return this;
        }

        /**
         * Sets the type of the network operation
         * @param networkOperation the type of the network operation
         * @return the current builder instance
         */
        public Builder networkOperation(NetworkOperation networkOperation) {
            this.networkOperation = networkOperation;
            return this;
        }

        @Override
        public NetworkOperationEvent build() {
            return new NetworkOperationEvent(this);
        }

        @Override
        protected void returnToParent(NetworkOperationEvent builtObj) {
            parentBuilder.networkOperationEvent(builtObj);
        }
    }
}
