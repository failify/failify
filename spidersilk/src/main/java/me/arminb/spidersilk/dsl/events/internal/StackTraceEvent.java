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
 * This is an internal event to match a specific stack trace in runtime
 */
public class StackTraceEvent extends InternalEvent {
    private final String stack;

    private StackTraceEvent(StackTraceEventBuilder builder) {
        super(builder.getName(), builder.getNodeName());
        stack = builder.stack;
    }

    public String getStack() {
        return stack;
    }

    @Override
    public List<InstrumentationDefinition> generateInstrumentationDefinitions(Deployment deployment) {
        List<InstrumentationDefinition> retList = new ArrayList<>();
        retList.add(InstrumentationDefinition.builder()
                .instrumentationPoint(stack.trim().split(",")[stack.trim().split(",").length - 1], InstrumentationPoint.Position.BEFORE)
                .withInstrumentationOperation(SpiderSilkRuntimeOperation.ENFORCE_ORDER)
                    .parameter(getName())
                    .parameter(stack).and()
                .build()
        );
        return retList;
    }

    public static class StackTraceEventBuilder extends InternalEventBuilder<StackTraceEvent> {
        private String stack;

        public StackTraceEventBuilder(Node.NodeBuilder parentBuilder, String name, String nodeName) {
            super(parentBuilder, name, nodeName);
            stack = "";
        }

        public StackTraceEventBuilder(String name, String nodeName) {
            super(name, nodeName);
        }

        public StackTraceEventBuilder(StackTraceEvent instance) {
            super(instance);
            stack = instance.stack;
        }

        public StackTraceEventBuilder trace(String trace) {
            stack += trace + ",";
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