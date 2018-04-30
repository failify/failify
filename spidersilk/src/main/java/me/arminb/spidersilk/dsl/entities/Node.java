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
import me.arminb.spidersilk.dsl.events.internal.SchedulingOperation;
import me.arminb.spidersilk.dsl.events.internal.StackTraceEvent;
import me.arminb.spidersilk.exceptions.DeploymentEntityNotFound;
import me.arminb.spidersilk.exceptions.PathNotFoundException;
import me.arminb.spidersilk.util.PathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * An abstraction for a node in a distributed system which acts as a container for internal events definition of a node
 */
public class Node extends ReferableDeploymentEntity {
    private final Map<String, PathEntry> applicationPaths;
    private final Map<String, String> environmentVariables;
    private final Set<String> logFiles;
    private final String logFolder;
    private final String serviceName;
    private final String runCommand;
    private final Map<String, InternalEvent> internalEvents;
    private final Boolean offOnStartup;
    private final Integer pathOrderCounter;
    private final String appHomeEnvVar;

    private Node(NodeBuilder builder) {
        super(builder.getName());
        serviceName = builder.serviceName;
        runCommand = builder.runCommand;
        internalEvents = Collections.unmodifiableMap(builder.internalEvents);
        offOnStartup = builder.offOnStartup;
        applicationPaths = Collections.unmodifiableMap(builder.applicationPaths);
        environmentVariables = Collections.unmodifiableMap(builder.environmentVariables);
        logFiles = Collections.unmodifiableSet(builder.logFiles);
        logFolder = builder.logFolder;
        pathOrderCounter = builder.pathOrderCounter;
        appHomeEnvVar = builder.appHomeEnvVar;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getRunCommand() {
        return runCommand;
    }

    public InternalEvent getInternalEvent(String name) {
        return internalEvents.get(name);
    }

    public Boolean getOffOnStartup() {
        return offOnStartup;
    }

    public Map<String, InternalEvent> getInternalEvents() {
        return internalEvents;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public Set<String> getLogFiles() {
        return logFiles;
    }

    public String getLogFolder() {
        return logFolder;
    }

    public Map<String, PathEntry> getApplicationPaths() {
        return applicationPaths;
    }

    public String getAppHomeEnvVar() {
        return appHomeEnvVar;
    }

    public static class NodeBuilder extends DeploymentBuilderBase<Node, Deployment.DeploymentBuilder> {
        private static Logger logger = LoggerFactory.getLogger(NodeBuilder.class);

        private Map<String, PathEntry> applicationPaths;
        private Map<String, String> environmentVariables;
        private Set<String> logFiles;
        private String logFolder;
        private final String serviceName;
        private String runCommand;
        private Map<String, InternalEvent> internalEvents;
        private Boolean offOnStartup;
        private Integer pathOrderCounter;
        private String appHomeEnvVar;

        public NodeBuilder(Deployment.DeploymentBuilder parentBuilder, String name, String serviceName) {
            super(parentBuilder, name);
            this.serviceName = serviceName;
            offOnStartup = false;
            internalEvents = new HashMap<>();
            applicationPaths = new HashMap<>();
            environmentVariables = new HashMap<>();
            logFiles = new HashSet<>();
            logFolder = null;
            pathOrderCounter = 0;
            appHomeEnvVar = null;
        }

        public NodeBuilder(String name, String serviceName) {
            this(null, name, serviceName);
        }

        public NodeBuilder(Deployment.DeploymentBuilder parentBuilder, Node instance) {
            super(parentBuilder, instance);
            serviceName = new String(instance.serviceName);
            runCommand = new String(instance.runCommand);
            offOnStartup = new Boolean(instance.offOnStartup);
            internalEvents = new HashMap<>(instance.internalEvents);
            applicationPaths = new HashMap<>(instance.applicationPaths);
            environmentVariables = new HashMap<>(instance.environmentVariables);
            logFiles = new HashSet<>(instance.logFiles);
            logFolder = new String(instance.logFolder);
            pathOrderCounter = new Integer(instance.pathOrderCounter);
            appHomeEnvVar = new String(instance.appHomeEnvVar);
        }

        public NodeBuilder(Node instance) {
            this(null, instance);
        }

        public StackTraceEvent.StackTraceEventBuilder withStackTraceEvent(String name) {
            return new StackTraceEvent.StackTraceEventBuilder(this, name, this.name);
        }

        public StackTraceEvent.StackTraceEventBuilder stackTraceEvent(String eventName) {
            if (!internalEvents.containsKey(eventName) || !(internalEvents.get(eventName) instanceof StackTraceEvent)) {
                throw new DeploymentEntityNotFound(eventName, "StackTraceEvent");
            }
            return new StackTraceEvent.StackTraceEventBuilder(this,
                    (StackTraceEvent) internalEvents.get(eventName));
        }

        public NodeBuilder stackTraceEvent(StackTraceEvent stackTraceEvent) {
            addInternalEvent(stackTraceEvent);
            return this;
        }

        public NodeBuilder stackTrace(String eventName, String stack) {
            String[] stackParts = stack.trim().split(",");
            StackTraceEvent.StackTraceEventBuilder builder = this.withStackTraceEvent(eventName);
            for (String part: stackParts) {
                builder.trace(part);
            }

            return builder.and();
        }

        public SchedulingEvent.SchedulingEventBuilder withSchedulingEvent(String name) {
            return new SchedulingEvent.SchedulingEventBuilder(this, name, this.name);
        }

        public SchedulingEvent.SchedulingEventBuilder schedulingEvent(String eventName) {
            if (!internalEvents.containsKey(eventName) || !(internalEvents.get(eventName) instanceof SchedulingEvent)) {
                throw new DeploymentEntityNotFound(eventName, "SchedulingEvent");
            }
            return new SchedulingEvent.SchedulingEventBuilder(this,
                    (SchedulingEvent) internalEvents.get(eventName));
        }

        public NodeBuilder schedulingEvent(SchedulingEvent schedulingEvent) {
            addInternalEvent(schedulingEvent);
            return this;
        }

        public NodeBuilder blockBefore(String eventName, String stackTraceEventName) {
            return withSchedulingEvent(eventName)
                    .operation(SchedulingOperation.BLOCK)
                    .before(stackTraceEventName).and();
        }

        public NodeBuilder blockAfter(String eventName, String stackTraceEventName) {
            return withSchedulingEvent(eventName)
                    .operation(SchedulingOperation.BLOCK)
                    .after(stackTraceEventName).and();
        }

        public NodeBuilder unblockBefore(String eventName, String stackTraceEventName) {
            return withSchedulingEvent(eventName)
                    .operation(SchedulingOperation.UNBLOCK)
                    .before(stackTraceEventName).and();
        }

        public NodeBuilder unblockAfter(String eventName, String stackTraceEventName) {
            return withSchedulingEvent(eventName)
                    .operation(SchedulingOperation.UNBLOCK)
                    .after(stackTraceEventName).and();
        }

        public GarbageCollectionEvent.GarbageCollectionEventBuilder withGarbageCollectionEvent(String name) {
            return new GarbageCollectionEvent.GarbageCollectionEventBuilder(this, name, this.name);
        }

        public NodeBuilder garbageCollectionEvent(GarbageCollectionEvent garbageCollectionEvent) {
            addInternalEvent(garbageCollectionEvent);
            return this;
        }

        public NodeBuilder garbageCollection(String eventName) {
            return withGarbageCollectionEvent(eventName).and();
        }

        private void addInternalEvent(InternalEvent event) {
            if (internalEvents.containsKey(event.getName())) {
                logger.warn("The internal event " + event.getName() + " is being redefined in the node "
                        + name + " definition!");
            }
            internalEvents.put(event.getName(), event);
        }

        public NodeBuilder offOnStartup() {
            this.offOnStartup = true;
            return this;
        }

        public NodeBuilder runCommand(String runCommand) {
            this.runCommand = runCommand;
            return this;
        }

        public NodeBuilder applicationPath(String path) {
            applicationPath(path, PathUtil.getLastFolderOrFileName(path));
            return this;
        }

        public NodeBuilder applicationPath(String path, String targetPath) {
            if (!new File(path).exists()) {
                throw new PathNotFoundException(path);
            }

            this.applicationPaths.put(path, new PathEntry(
                    path, targetPath, false, pathOrderCounter++));
            return this;
        }

        public NodeBuilder environmentVariable(String name, String value) {
            this.environmentVariables.put(name, value);
            return this;
        }

        public NodeBuilder logFile(String path) {
            logFiles.add(path);
            return this;
        }

        public NodeBuilder logFolder(String path) {
            this.logFolder = path;
            return this;
        }

        public NodeBuilder exposeAppHomeDirectoryAs(String appHomeEnvVar) {
            this.appHomeEnvVar = appHomeEnvVar;
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
