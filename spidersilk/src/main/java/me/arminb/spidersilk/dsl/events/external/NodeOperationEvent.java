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
 *
 */

package me.arminb.spidersilk.dsl.events.external;

import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.dsl.events.ExternalEvent;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.execution.RuntimeEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstraction for defining an operation on top of a node. The operation can a choice be between making a node down, making a node up
 * and resetting a node.
 */
public class NodeOperationEvent extends ExternalEvent {
    private final static Logger logger = LoggerFactory.getLogger(NodeOperationEvent.class);
    private final NodeOperation nodeOperation;
    private final String nodeName;
    private final Integer secondsUntilForcedStop;

    protected NodeOperationEvent(NodeOperationEventBuilder builder) {
        super(builder.getName());
        nodeName = builder.nodeName;
        nodeOperation = builder.nodeOperation;
        if (builder.secondsUntilForcedStop == null) {
            if (nodeOperation == NodeOperation.STOP) {
                secondsUntilForcedStop = Constants.DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_STOP;
            } else {
                secondsUntilForcedStop = Constants.DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_RESTART;
            }
        } else {
            secondsUntilForcedStop = builder.secondsUntilForcedStop;
        }
    }

    @Override
    protected void execute(RuntimeEngine runtimeEngine) {
        // TODO this should support node up and reset
        switch (nodeOperation) {
            case KILL:
                try {
                    runtimeEngine.killNode(nodeName);
                } catch (RuntimeEngineException e) {
                    logger.info("Error while trying to kill node {}!", nodeName);
                }
            case STOP:
                try {
                    runtimeEngine.stopNode(nodeName, secondsUntilForcedStop);
                } catch (RuntimeEngineException e) {
                    logger.info("Error while trying to stop node {}!", nodeName);
                }
            case RESET:
                try {
                    runtimeEngine.restartNode(nodeName, secondsUntilForcedStop);
                } catch (RuntimeEngineException e) {
                    logger.info("Error while trying to restart node {}!", nodeName);
                }
            case START:
                try {
                    runtimeEngine.startNode(nodeName);
                } catch (RuntimeEngineException e) {
                    logger.info("Error while trying to start node {}!", nodeName);
                }
        }

    }

    public NodeOperation getNodeOperation() {
        return nodeOperation;
    }

    public String getNodeName() {
        return nodeName;
    }

    public Integer getSecondsUntilForcedStop() {
        return secondsUntilForcedStop;
    }

    public static class NodeOperationEventBuilder extends DeploymentBuilderBase<NodeOperationEvent, Deployment.DeploymentBuilder> {
        private NodeOperation nodeOperation;
        private String nodeName;
        private Integer secondsUntilForcedStop;

        public NodeOperationEventBuilder(String name) {
            this(null, name);
        }

        public NodeOperationEventBuilder(Deployment.DeploymentBuilder parentBuilder, String name) {
            super(parentBuilder, name);
            secondsUntilForcedStop = null;
        }

        public NodeOperationEventBuilder(NodeOperationEvent instance) {
            super(null, instance);
        }

        public NodeOperationEventBuilder(Deployment.DeploymentBuilder parentBuilder, NodeOperationEvent instance) {
            super(parentBuilder, instance);
            nodeName = new String(instance.nodeName);
            nodeOperation = instance.nodeOperation;
            secondsUntilForcedStop = new Integer(instance.secondsUntilForcedStop);
        }

        public NodeOperationEventBuilder nodeName(String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        public NodeOperationEventBuilder nodeOperation(NodeOperation nodeOperation) {
            this.nodeOperation = nodeOperation;
            return this;
        }

        public NodeOperationEventBuilder secondsUntilForcedStop(Integer secondsUntilForcedStop) {
            this.secondsUntilForcedStop = secondsUntilForcedStop;
            return this;
        }

        @Override
        public NodeOperationEvent build() {
            return new NodeOperationEvent(this);
        }

        @Override
        protected void returnToParent(NodeOperationEvent builtObj) {
            parentBuilder.nodeOperationEvent(builtObj);
        }
    }
}
