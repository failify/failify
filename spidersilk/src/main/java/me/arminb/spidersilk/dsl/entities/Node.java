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

package me.arminb.spidersilk.dsl.entities;

import me.arminb.spidersilk.dsl.ReferableDeploymentEntity;
import me.arminb.spidersilk.dsl.events.InternalEvent;
import me.arminb.spidersilk.dsl.events.internal.GarbageCollectionEvent;
import me.arminb.spidersilk.dsl.events.internal.SchedulingEvent;
import me.arminb.spidersilk.dsl.events.internal.StackTraceEvent;
import me.arminb.spidersilk.exceptions.DeploymentEntityNameConflictException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An abstraction for a node in a distributed system which acts as a container for internal events definition of a node
 */
public class Node extends ReferableDeploymentEntity {
    private final String serviceName;
    private final Map<String, InternalEvent> internalEvents;

    private Node(NodeBuilder builder) {
        super(builder.getName());
        serviceName = builder.serviceName;
        internalEvents = Collections.unmodifiableMap(builder.internalEvents);
    }

    public String getServiceName() {
        return serviceName;
    }

    public InternalEvent getInternalEvent(String name) {
        return internalEvents.get(name);
    }

    public Map<String, InternalEvent> getInternalEvents() {
        return internalEvents;
    }

    public static class NodeBuilder extends DeploymentBuilderBase<Node, Deployment.DeploymentBuilder> {
        private String serviceName;
        private Map<String, InternalEvent> internalEvents;

        public NodeBuilder(Deployment.DeploymentBuilder parentBuilder, String name) {
            super(parentBuilder, name);
            internalEvents = new HashMap<>();
        }

        public NodeBuilder(String name) {
            super(name);
        }

        public NodeBuilder(Node instance) {
            super(instance);
            serviceName = instance.serviceName;
            internalEvents = new HashMap<>(instance.internalEvents);
        }

        public StackTraceEvent.StackTraceEventBuilder withStackTraceEvent(String name) {
            return new StackTraceEvent.StackTraceEventBuilder(this, name, this.name);
        }

        public NodeBuilder stackTraceEvent(StackTraceEvent stackTraceEvent) {
            addInternalEvent(stackTraceEvent);
            return this;
        }

        public SchedulingEvent.SchedulingEventBuilder withSchedulingEvent(String name) {
            return new SchedulingEvent.SchedulingEventBuilder(this, name, this.name);
        }

        public NodeBuilder schedulingEvent(SchedulingEvent schedulingEvent) {
            addInternalEvent(schedulingEvent);
            return this;
        }

        public GarbageCollectionEvent.GarbageCollectionEventBuilder withGarbageCollectionEvent(String name) {
            return new GarbageCollectionEvent.GarbageCollectionEventBuilder(this, name, this.name);
        }

        public NodeBuilder garbageCollectionEvent(GarbageCollectionEvent garbageCollectionEvent) {
            addInternalEvent(garbageCollectionEvent);
            return this;
        }

        private void addInternalEvent(InternalEvent event) {
            if (internalEvents.containsKey(event.getName())) {
                throw new DeploymentEntityNameConflictException(event.getName());
            }
            internalEvents.put(event.getName(), event);
        }

        public NodeBuilder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        @Override
        public Node build() {
            return new Node(this);
        }

        @Override
        protected void returnToParent(Node builtObj) {
            parentBuilder.node(builtObj);
        }
    }
}
