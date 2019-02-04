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

import io.failify.Constants;
import io.failify.dsl.entities.Deployment;
import io.failify.dsl.entities.Node;
import io.failify.instrumentation.InstrumentationDefinition;
import io.failify.instrumentation.InstrumentationPoint;
import io.failify.instrumentation.RunSeqRuntimeOperation;
import io.failify.dsl.events.InternalEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * This is an internal event to start a garbage collection task in a node
 */
public class GarbageCollectionEvent extends InternalEvent {
    private GarbageCollectionEvent(Builder builder) {
        super(builder.getName(), builder.getNodeName());
    }

    @Override
    public List<InstrumentationDefinition> generateInstrumentationDefinitions(Deployment deployment) {
        List<InstrumentationDefinition> retList = new ArrayList<>();
        retList.add(InstrumentationDefinition.builder()
                .instrumentationPoint(Constants.INSTRUMENTATION_POINT_MAIN, InstrumentationPoint.Position.BEFORE)
                .withInstrumentationOperation(RunSeqRuntimeOperation.GARBAGE_COLLECTION)
                    .parameter(name).and()
                .build()
        );
        return retList;
    }

    /**
     * The builder class for building a garbage collection event
     */
    public static class Builder extends InternalEventBuilder<GarbageCollectionEvent> {

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param name the name of the garbage collection event to be built
         * @param nodeName the node name to apply garbage collection in
         */
        public Builder(Node.Builder parentBuilder, String name, String nodeName) {
            super(parentBuilder, name, nodeName);
        }

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param instance a garbage collection event object instance to be changed
         */
        public Builder(Node.Builder parentBuilder, GarbageCollectionEvent instance) {
            super(parentBuilder, instance);
        }

        @Override
        public GarbageCollectionEvent build() {
            return new GarbageCollectionEvent(this);
        }

        @Override
        protected void returnToParent(GarbageCollectionEvent builtObj) {
            parentBuilder.garbageCollectionEvent(builtObj);
        }
    }
}
