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

package me.arminb.spidersilk.dsl.events.internal;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.events.InternalEvent;
import me.arminb.spidersilk.instrumentation.InstrumentationDefinition;
import me.arminb.spidersilk.instrumentation.InstrumentationPoint;
import me.arminb.spidersilk.instrumentation.SpiderSilkRuntimeOperation;

import java.util.ArrayList;
import java.util.List;

/**
 * This is an internal event to block or unblock intentionally after or before a stack trace event
 */
public class SchedulingEvent extends BlockingEvent {
    private final SchedulingOperation operation;
    private final SchedulingPoint schedulingPoint;
    private final String targetEventName;

    private SchedulingEvent(SchedulingEventBuilder builder) {
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
                    .withInstrumentationOperation(SpiderSilkRuntimeOperation.ENFORCE_ORDER)
                        .parameter(name)
                        .parameter(stack).and()
                    .build()
            );
        }

        return retList;
    }

    public static class SchedulingEventBuilder extends InternalEventBuilder<SchedulingEvent> {
        private SchedulingOperation operation;
        private SchedulingPoint schedulingPoint;
        private String targetEventName;

        public SchedulingEventBuilder(String name, String nodeName) {
            super(name, nodeName);
        }

        public SchedulingEventBuilder(Node.NodeBuilder parentBuilder, String name, String nodeName) {
            super(parentBuilder, name, nodeName);
        }

        public SchedulingEventBuilder(SchedulingEvent instance) {
            super(instance);
            operation = instance.operation;
        }

        public SchedulingEventBuilder operation(SchedulingOperation operation) {
            this.operation = operation;
            return this;
        }

        public SchedulingEventBuilder before(String targetEventName) {
            this.targetEventName = targetEventName;
            this.schedulingPoint = SchedulingPoint.BEFORE;
            return this;
        }

        public SchedulingEventBuilder after(String targetEventName) {
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
