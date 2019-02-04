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
 *
 */

package io.failify.dsl.events.external;

import io.failify.Constants;
import io.failify.dsl.entities.Deployment;
import io.failify.execution.LimitedRuntimeEngine;
import io.failify.dsl.events.ExternalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an external event to execute a node operation including start, stop, restart and kill a node
 */
public class NodeOperationEvent extends ExternalEvent {
    private final static Logger logger = LoggerFactory.getLogger(NodeOperationEvent.class);
    private final NodeOperation nodeOperation; // the type of node operation
    private final String nodeName; // the node name to apply the operation on
    private final Integer secondsUntilForcedStop; // the seconds until forced stop in case node stop is needed


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
    protected void execute(LimitedRuntimeEngine runtimeEngine) throws Exception {
        switch (nodeOperation) {
            case KILL:
                runtimeEngine.killNode(nodeName); break;
            case STOP:
                runtimeEngine.stopNode(nodeName, secondsUntilForcedStop); break;
            case RESET:
                runtimeEngine.restartNode(nodeName, secondsUntilForcedStop); break;
            case START:
                runtimeEngine.startNode(nodeName); break;
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

    /**
     * The builder class for building a node operation event
     */
    public static class NodeOperationEventBuilder extends BuilderBase<NodeOperationEvent, Deployment.Builder> {
        private NodeOperation nodeOperation;
        private String nodeName;
        private Integer secondsUntilForcedStop;

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param name the name of the node operation event to be built
         */
        public NodeOperationEventBuilder(Deployment.Builder parentBuilder, String name) {
            super(parentBuilder, name);
            secondsUntilForcedStop = null;
        }

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param instance a node operation event object instance to be changed
         */
        public NodeOperationEventBuilder(Deployment.Builder parentBuilder, NodeOperationEvent instance) {
            super(parentBuilder, instance);
            nodeName = new String(instance.nodeName);
            nodeOperation = instance.nodeOperation;
            secondsUntilForcedStop = new Integer(instance.secondsUntilForcedStop);
        }

        /**
         * Sets the node name to apply the operation on
         * @param nodeName the node name to apply the operation on
         * @return the current builder instance
         */
        public NodeOperationEventBuilder nodeName(String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        /**
         * Sets the type of node operation
         * @param nodeOperation the type of node operation
         * @return the current builder instance
         */
        public NodeOperationEventBuilder nodeOperation(NodeOperation nodeOperation) {
            this.nodeOperation = nodeOperation;
            return this;
        }

        /**
         * Sets the seconds until forced stop in case node stop is needed
         * @param secondsUntilForcedStop the seconds until forced stop in case node stop is needed
         * @return the current builder instance
         */
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
