/*
 * MIT License
 *
 * Copyright (c) 2017 Armin Balalaie
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
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.execution.LimitedRuntimeEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkOperationEvent extends ExternalEvent {
    private final static Logger logger = LoggerFactory.getLogger(NetworkOperationEvent.class);

    private final String nodePartitions;
    private final NetworkOperation networkOperation;

    private NetworkOperationEvent(NetworkOperationEventBuilder builder) {
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
        }
    }

    public String getNodePartitions() {
        return nodePartitions;
    }

    public static class NetworkOperationEventBuilder extends DeploymentBuilderBase<NetworkOperationEvent, Deployment.DeploymentBuilder> {
        private String nodePartitions;
        private NetworkOperation networkOperation;

        public NetworkOperationEventBuilder(Deployment.DeploymentBuilder parentBuilder, String name) {
            super(parentBuilder, name);
            nodePartitions = null;
        }

        public NetworkOperationEventBuilder(Deployment.DeploymentBuilder parentBuilder, NetworkOperationEvent instance) {
            super(parentBuilder, instance);
            nodePartitions = new String(instance.nodePartitions);
            networkOperation = instance.networkOperation;
        }

        public NetworkOperationEventBuilder nodePartitions(String nodePartitions) {
            this.nodePartitions = nodePartitions;
            return this;
        }

        public NetworkOperationEventBuilder networkOperation(NetworkOperation networkOperation) {
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
