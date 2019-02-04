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

package io.failify.dsl.events.internal;

import io.failify.dsl.entities.Deployment;
import io.failify.dsl.entities.Node;
import io.failify.instrumentation.InstrumentationDefinition;
import io.failify.instrumentation.InstrumentationPoint;
import io.failify.instrumentation.RunSeqRuntimeOperation;

import java.util.ArrayList;
import java.util.List;

/**
 * This is an internal event to block or unblock intentionally after or before a stack trace event
 */
public class SchedulingEvent extends BlockingEvent {
    private final SchedulingOperation operation; // block or unblock operation
    private final SchedulingPoint schedulingPoint; // after or before a method
    private final String targetEventName; // the event name to use its stack trace

    private SchedulingEvent(Builder builder) {
        super(builder.getName(), builder.getNodeName());
        operation = builder.operation;
        schedulingPoint = builder.schedulingPoint;
        targetEventName = builder.targetEventName;
    }

    public SchedulingOperation getOperation() {
        return operation;
    }

    public SchedulingPoint getSchedulingPoint() {
        return schedulingPoint;
    }

    public String getTargetEventName() {
        return targetEventName;
    }

    @Override
    public boolean isBlocking() {
        // we only have blocking instrumentation for unblock
        return operation == SchedulingOperation.UNBLOCK;
    }

    @Override
    public String getStack(Deployment deployment) {
        return ((StackTraceEvent) deployment.getNode(getNodeName()).getInternalEvent(targetEventName)).getStack();
    }

    @Override
    public List<InstrumentationDefinition> generateInstrumentationDefinitions(Deployment deployment) {
        List<InstrumentationDefinition> retList = new ArrayList<>();

        String stack = getStack(deployment);

        InstrumentationPoint.Position instrumentationPosition = schedulingPoint == SchedulingPoint.BEFORE ?
                InstrumentationPoint.Position.BEFORE : InstrumentationPoint.Position.AFTER;

        if (operation == SchedulingOperation.UNBLOCK) {
            retList.add(InstrumentationDefinition.builder()
                    .instrumentationPoint(stack.trim().split(",")[stack.trim().split(",").length - 1], instrumentationPosition)
                    .withInstrumentationOperation(RunSeqRuntimeOperation.ENFORCE_ORDER)
                        .parameter(name)
                        .parameter(stack).and()
                    .build()
            );
        }

        return retList;
    }

    /**
     * The builder class for building a scheduling event
     */
    public static class Builder extends InternalEventBuilder<SchedulingEvent> {
        private SchedulingOperation operation;
        private SchedulingPoint schedulingPoint;
        private String targetEventName;

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param name the name of the scheduling event to be built
         * @param nodeName the node name to apply scheduling operation in
         */
        public Builder(Node.Builder parentBuilder, String name, String nodeName) {
            super(parentBuilder, name, nodeName);
        }

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param instance a scheduling event object instance to be changed
         */
        public Builder(Node.Builder parentBuilder, SchedulingEvent instance) {
            super(parentBuilder, instance);
            operation = instance.operation;
            schedulingPoint = instance.schedulingPoint;
            targetEventName = instance.targetEventName;
        }

        /**
         * Sets the operation to either blocking or unblocking
         * @param operation the type of operation for this event
         * @return the current builder instance
         */
        public Builder operation(SchedulingOperation operation) {
            this.operation = operation;
            return this;
        }

        /**
         * Sets the blocking after the defined stack trace of the given stack trace event name
         * @param targetEventName a stack trace event name
         * @return the current builder instance
         */
        public Builder before(String targetEventName) {
            this.targetEventName = targetEventName;
            this.schedulingPoint = SchedulingPoint.BEFORE;
            return this;
        }

        /**
         * Sets the blocking before the defined stack trace of the given stack trace event name
         * @param targetEventName a stack trace event name
         * @return the current builder instance
         */
        public Builder after(String targetEventName) {
            this.targetEventName = targetEventName;
            this.schedulingPoint = SchedulingPoint.AFTER;
            return this;
        }

        @Override
        public SchedulingEvent build() {
            return new SchedulingEvent(this);
        }

        @Override
        protected void returnToParent(SchedulingEvent builtObj) {
            parentBuilder.schedulingEvent(builtObj);
        }
    }
}
