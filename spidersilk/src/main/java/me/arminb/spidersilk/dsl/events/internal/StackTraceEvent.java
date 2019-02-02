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

package me.arminb.spidersilk.dsl.events.internal;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.instrumentation.InstrumentationDefinition;
import me.arminb.spidersilk.instrumentation.InstrumentationPoint;
import me.arminb.spidersilk.instrumentation.SpiderSilkRuntimeOperation;

import java.util.ArrayList;
import java.util.List;

/**
 * This is an internal event to match a specific stack trace in runtime
 */
public class StackTraceEvent extends BlockingEvent {
    private final String stack; // the stack trace to block after or before
    private final SchedulingPoint schedulingPoint; // before or after

    private StackTraceEvent(Builder builder) {
        super(builder.getName(), builder.getNodeName());
        stack = builder.stack;
        schedulingPoint = builder.schedulingPoint;
    }

    public String getStack() {
        return stack;
    }

    @Override
    public String getStack(Deployment deployment) {
        return stack;
    }

    @Override
    public List<InstrumentationDefinition> generateInstrumentationDefinitions(Deployment deployment) {
        List<InstrumentationDefinition> retList = new ArrayList<>();
        InstrumentationPoint.Position instrumentationPoint = schedulingPoint == SchedulingPoint.BEFORE ?
                InstrumentationPoint.Position.BEFORE : InstrumentationPoint.Position.AFTER;
        retList.add(InstrumentationDefinition.builder()
                .instrumentationPoint(stack.trim().split(",")[stack.trim().split(",").length - 1], instrumentationPoint)
                .withInstrumentationOperation(SpiderSilkRuntimeOperation.ENFORCE_ORDER)
                    .parameter(getName())
                    .parameter(stack).and()
                .build()
        );
        return retList;
    }

    @Override
    public SchedulingPoint getSchedulingPoint() {
        return schedulingPoint;
    }

    /**
     * The builder class for building a scheduling event
     */
    public static class Builder extends InternalEventBuilder<StackTraceEvent> {
        private String stack;
        private SchedulingPoint schedulingPoint;

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param name the name of the stack trace event to be built
         * @param nodeName the node name that this stack trace event belongs to
         */
        public Builder(Node.Builder parentBuilder, String name, String nodeName) {
            super(parentBuilder, name, nodeName);
            stack = "";
            schedulingPoint = SchedulingPoint.BEFORE;
        }

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param instance a stack trace event object instance to be changed
         */
        public Builder(Node.Builder parentBuilder, StackTraceEvent instance) {
            super(parentBuilder, instance);
            stack = new String(instance.stack);
            schedulingPoint = instance.schedulingPoint;
        }

        /**
         * Adds a method address (trace) (e.g. package.class.method) to the stack trace
         * @param trace the method address
         * @return the current builder instance
         */
        public Builder trace(String trace) {
            stack += trace.trim() + ",";
            return this;
        }

        /**
         * By default stack trace events will be blocked before the last method in the stack trace. Calling this method
         * will make the blocking to happen after the last method
         * @return the current builder instance
         */
        public Builder blockAfter() {
            schedulingPoint = SchedulingPoint.AFTER;
            return this;
        }

        @Override
        public StackTraceEvent build() {
            if (!stack.isEmpty()) {
                stack = stack.substring(0,stack.length() - 1);
            }
            return new StackTraceEvent(this);
        }

        @Override
        protected void returnToParent(StackTraceEvent builtObj) {
            parentBuilder.stackTraceEvent(builtObj);
        }
    }
}