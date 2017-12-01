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

import me.arminb.spidersilk.dsl.DeploymentEntity;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.ReferableDeploymentEntity;
import me.arminb.spidersilk.dsl.events.InternalEvent;
import me.arminb.spidersilk.instrumentation.InstrumentationDefinition;
import me.arminb.spidersilk.instrumentation.InstrumentationOperation;
import me.arminb.spidersilk.instrumentation.SpecialInstrumentationPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This is an internal event to start a garbage collection task in a node
 */
public class GarbageCollectionEvent extends InternalEvent {
    private GarbageCollectionEvent(GarbageCollectionEventBuilder builder) {
        super(builder.getName(), builder.getNodeName());
    }

    @Override
    public List<InstrumentationDefinition> generateInstrumentationDefinitions(Deployment deployment) {
        List<InstrumentationDefinition> retList = new ArrayList<>();
        retList.add(InstrumentationDefinition.builder()
                .instrumentationPoint(SpecialInstrumentationPoint.MAIN)
                .instrumentationOperation(InstrumentationOperation.GARBAGE_COLLECTION)
                .addOperationParameter(getName())
                .build()
        );
        return retList;
    }

    public static class GarbageCollectionEventBuilder extends InternalEventBuilder<GarbageCollectionEvent> {
        public GarbageCollectionEventBuilder(String name, String nodeName) {
            super(name, nodeName);
        }

        public GarbageCollectionEventBuilder(Node.NodeBuilder parentBuilder, String name, String nodeName) {
            super(parentBuilder, name, nodeName);
        }

        public GarbageCollectionEventBuilder(GarbageCollectionEvent instance) {
            super(instance);
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
