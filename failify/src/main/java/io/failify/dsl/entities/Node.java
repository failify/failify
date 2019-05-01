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

package io.failify.dsl.entities;

import io.failify.exceptions.DeploymentEntityNotFound;
import io.failify.util.FileUtil;
import io.failify.dsl.ReferableDeploymentEntity;
import io.failify.dsl.events.InternalEvent;
import io.failify.dsl.events.internal.GarbageCollectionEvent;
import io.failify.dsl.events.internal.SchedulingEvent;
import io.failify.dsl.events.internal.SchedulingOperation;
import io.failify.dsl.events.internal.StackTraceEvent;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * An abstraction for a node in a distributed system which acts as a container for internal events definition of a node.
 * It is possible to define applications paths, environment variables, log files and directories to be collected, ports
 * to be exposed in the node's container (if necessary).
 */
public class Node extends ReferableDeploymentEntity {
    private final Map<String, PathEntry> applicationPaths; // map of local paths to absolute target paths for the node
    private final Set<ExposedPortDefinition> exposedPorts; // set of exposed TCP or UDP ports for the node
    private final Map<String, String> environmentVariables; // map of env vars name to value
    private final Set<String> logFiles; // set of target log files to be collected
    private final Set<String> logDirectories; // set of target log directories to be collected
    private final String serviceName; // the service name for the node
    private final String initCommand; // the init command of the node which will executed only once
    private final String startCommand; // the start command of the node which will executed when the node is started or restarted
    private final String stopCommand; // the stop command of the node which will executed when the node is stopped or restarted
    private final Map<String, InternalEvent> internalEvents; // the map of internal event names to their objects
    private final Boolean offOnStartup; // the flag to start the node on start up or not
    private final Boolean disableClockDrift; // the flag to disable clock drift capability
    private final Integer pathOrderCounter; // the counter to use for applying order to application paths

    public static Node.LimitedBuilder limitedBuilder(String nodeName, String serviceName) {
        return new Node.LimitedBuilder(nodeName, serviceName);
    }

    /**
     * Private Constructor
     * @param builder the builder instance to use for creating the class instance
     */
    private Node(Builder builder) {
        super(builder.getName());
        serviceName = builder.serviceName;
        initCommand = builder.initCommand;
        startCommand = builder.startCommand;
        stopCommand = builder.stopCommand;
        internalEvents = Collections.unmodifiableMap(builder.internalEvents);
        offOnStartup = builder.offOnStartup;
        applicationPaths = Collections.unmodifiableMap(builder.applicationPaths);
        exposedPorts = builder.exposedPorts;
        environmentVariables = Collections.unmodifiableMap(builder.environmentVariables);
        logFiles = Collections.unmodifiableSet(builder.logFiles);
        logDirectories = builder.logDirectories;
        disableClockDrift = builder.disableClockDrift;
        pathOrderCounter = builder.pathOrderCounter;
    }

    /**
     * Private Constructor
     * @param builder the builder instance to use for creating the class instance
     */
    private Node(LimitedBuilder builder) {
        super(builder.getName());
        serviceName = builder.serviceName;
        initCommand = builder.initCommand;
        startCommand = builder.startCommand;
        stopCommand = builder.stopCommand;
        internalEvents = Collections.unmodifiableMap(new HashMap<>());
        offOnStartup = false;
        applicationPaths = Collections.unmodifiableMap(builder.applicationPaths);
        exposedPorts = builder.exposedPorts;
        environmentVariables = Collections.unmodifiableMap(builder.environmentVariables);
        logFiles = Collections.unmodifiableSet(builder.logFiles);
        logDirectories = builder.logDirectories;
        disableClockDrift = builder.disableClockDrift;
        pathOrderCounter = builder.pathOrderCounter;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getInitCommand() {
        return initCommand;
    }

    public String getStartCommand() {
        return startCommand;
    }

    public String getStopCommand() {
        return stopCommand;
    }

    /**
     * @param name of the internal event
     * @return the internal event object matching the given name
     */
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

    public Set<String> getLogDirectories() {
        return logDirectories;
    }

    public Map<String, PathEntry> getApplicationPaths() {
        return applicationPaths;
    }

    public Set<ExposedPortDefinition> getExposedPorts() {
        return exposedPorts;
    }

    public Boolean isClockDriftEnabled() {
        return !disableClockDrift;
    }

    /**
     * The builder class to build a node object
     */
    public static class LimitedBuilder extends BuilderBase<Node, Deployment.Builder> {
        private static Logger logger = LoggerFactory.getLogger(LimitedBuilder.class);

        protected Map<String, PathEntry> applicationPaths;
        protected Set<ExposedPortDefinition> exposedPorts;
        protected Map<String, String> environmentVariables;
        protected Set<String> logFiles;
        protected Set<String> logDirectories;
        protected final String serviceName;
        protected String initCommand;
        protected String startCommand;
        protected String stopCommand;
        protected Boolean disableClockDrift; // the flag to disable clock drift capability
        protected Integer pathOrderCounter;

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param name the name of the node to be built
         * @param serviceName the service name for the node
         */
        protected LimitedBuilder(Deployment.Builder parentBuilder, String name, String serviceName) {
            super(parentBuilder, name);

            if (serviceName == null) {
                throw new NullPointerException("Service name for node " + name + " cannot be null");
            }

            this.serviceName = serviceName;
            applicationPaths = new HashMap<>();
            exposedPorts = new HashSet<>();
            environmentVariables = new HashMap<>();
            logFiles = new HashSet<>();
            logDirectories = new HashSet<>();
            disableClockDrift = false;
            pathOrderCounter = 0;
        }

        /**
         * Constructor
         * @param name the name of the node to be built
         * @param serviceName the service name for the node
         */
        public LimitedBuilder(String name, String serviceName) {
            this(null, name, serviceName);
        }

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param instance a node object instance to be changed
         */
        protected LimitedBuilder(Deployment.Builder parentBuilder, Node instance) {
            super(parentBuilder, instance);
            serviceName = new String(instance.serviceName);
            initCommand = new String(instance.initCommand);
            startCommand = new String(instance.startCommand);
            stopCommand = new String(instance.stopCommand);
            applicationPaths = new HashMap<>(instance.applicationPaths);
            exposedPorts = new HashSet<>(instance.exposedPorts);
            environmentVariables = new HashMap<>(instance.environmentVariables);
            logFiles = new HashSet<>(instance.logFiles);
            logDirectories = new HashSet<>(instance.logDirectories);
            disableClockDrift = new Boolean(instance.disableClockDrift);
            pathOrderCounter = new Integer(instance.pathOrderCounter);
        }

        /**
         * Sets the init command for the node which will be executed only once
         * @param initCommand the init command of the node
         * @return the current builder instance
         */
        public LimitedBuilder initCommand(String initCommand) {
            this.initCommand = initCommand;
            return this;
        }

        /**
         * Sets the start command for the node which will be executed when starting or restarting a node
         * @param startCommand the start command of the node
         * @return the current builder instance
         */
        public LimitedBuilder startCommand(String startCommand) {
            this.startCommand = startCommand;
            return this;
        }

        /**
         * Sets the stop command for the node which will be executed when stopping or restarting a node
         * @param stopCommand the stop command of the node
         * @return the current builder instance
         */
        public LimitedBuilder stopCommand(String stopCommand) {
            this.stopCommand = stopCommand;
            return this;
        }

        /**
         * The clock drift capability is being supported through the libfaketime library. This library has limitations and
         * may cause unexpected errors with some binaries. If you are seeing unexpected error messages that you normally
         * don't see, you should try disabling clock drift capability by calling this method.
         * @return the current builder instance
         */
        public LimitedBuilder disableClockDrift() {
            this.disableClockDrift = true;
            return this;
        }

        /**
         * Enables clock drift capability (enabled by default. Only call this if you have disabled it somewhere else)
         * @return the current builder instance
         */
        public LimitedBuilder enableClockDrift() {
            this.disableClockDrift = false;
            return this;
        }

        /**
         * Adds a not changing local path to the specified absolute target path in the node
         * @param path a local path
         * @param targetPath an absolute target path in the node's container
         * @return the current builder instance
         */
        public LimitedBuilder applicationPath(String path, String targetPath) {
            applicationPath(path, targetPath, false);
            return this;
        }

        /**
         * Adds a local path to the specified absolute target path in the node
         * @param path a local path
         * @param targetPath an absolute target path in the node's container
         * @param willBeChanged a flag to mark the path as changeable which results in a separate copy of the path for
         *                      each node
         * @return the current builder instance
         */
        public LimitedBuilder applicationPath(String path, String targetPath, Boolean willBeChanged) {
            this.applicationPaths.put(path, new PathEntry(
                    path, targetPath, false, willBeChanged, false, pathOrderCounter++)); // TODO Make this thread-safe
            return this;
        }

        /**
         * Adds an environment variable to the node
         * @param name the name of the variable
         * @param value the value of the variable
         * @return the current builder instance
         */
        public LimitedBuilder environmentVariable(String name, String value) {
            this.environmentVariables.put(name, value);
            return this;
        }

        /**
         * Adds a tcp port to be exposed by the node's container
         * @param portNumber the tcp port number to be exposed by the node
         * @return the current builder instance
         */
        public LimitedBuilder tcpPort(Integer... portNumber) {
            for (Integer port: portNumber) {
                exposedPorts.add(new ExposedPortDefinition(port, PortType.TCP));
            }
            return this;
        }

        /**
         * Adds a udp port to be exposed by the node's container
         * @param portNumber the udp port number to be exposed by the node
         * @return the current builder instance
         */
        public LimitedBuilder udpPort(Integer... portNumber) {
            for (Integer port: portNumber) {
                exposedPorts.add(new ExposedPortDefinition(port, PortType.UDP));
            }
            return this;
        }

        /**
         * Adds an absolute target path in node's container to be collected as a log file into the node's local workspace
         * @param path an absolute target log file path to be collected
         * @return the current builder instance
         */
        public LimitedBuilder logFile(String path) {
            if (!FileUtil.isPathAbsoluteInUnix(path)) {
                throw new RuntimeException("The log file `" + path + "` path is not absolute!");
            }
            logFiles.add(FilenameUtils.normalizeNoEndSeparator(path, true));
            return this;
        }

        /**
         * Adds an absolute target path in node's container to be collected as a log directory into the node's local workspace
         * @param path an absolute target log directory path to be collected
         * @return the current builder instance
         */
        public LimitedBuilder logDirectory(String path) {
            if (!FileUtil.isPathAbsoluteInUnix(path)) {
                throw new RuntimeException("The log directory `" + path + "` path is not absolute!");
            }
            this.logDirectories.add(FilenameUtils.normalizeNoEndSeparator(path, true));
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

    public static class Builder extends LimitedBuilder {
        private static Logger logger = LoggerFactory.getLogger(Builder.class);

        private Map<String, InternalEvent> internalEvents;
        private Boolean offOnStartup;

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param name the name of the node to be built
         * @param serviceName the service name for the node
         */
        public Builder(Deployment.Builder parentBuilder, String name, String serviceName) {
            super(parentBuilder, name, serviceName);
            offOnStartup = false;
            internalEvents = new HashMap<>();
        }

        /**
         * Constructor
         * @param name the name of the node to be built
         * @param serviceName the service name for the node
         */
        public Builder(String name, String serviceName) {
            this(null, name, serviceName);
        }

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param instance a node object instance to be changed
         */
        public Builder(Deployment.Builder parentBuilder, Node instance) {
            super(parentBuilder, instance);
            offOnStartup = new Boolean(instance.offOnStartup);
            internalEvents = new HashMap<>(instance.internalEvents);
        }

        /**
         * Constructor
         * @param instance a node object instance to be changed
         */
        public Builder(Node instance) {
            this(null, instance);
        }

        /**
         * Returns a stack trace event builder to define a new stack trace event object in the node definition
         * @param name of the stack trace event
         * @return a new stack trace event builder object initialized with the given name
         */
        public StackTraceEvent.Builder withStackTraceEvent(String name) {
            return new StackTraceEvent.Builder(this, name, this.name);
        }

        /**
         * Returns a stack trace event builder to change an existing stack trace event object in the deployment definition
         * with the given stack trace event name
         * @param eventName the stakc trace event name to be changed through a stack trace event builder
         * @return A stack trace event builder instance already initialized with an existing stack trace event object in
         * the deployment definition
         * @throws DeploymentEntityNotFound if a stack trace event object with the given name is not present in the
         * deployment definition
         */
        public StackTraceEvent.Builder stackTraceEvent(String eventName) {
            if (!internalEvents.containsKey(eventName) || !(internalEvents.get(eventName) instanceof StackTraceEvent)) {
                throw new DeploymentEntityNotFound(eventName, "StackTraceEvent");
            }
            return new StackTraceEvent.Builder(this,
                    (StackTraceEvent) internalEvents.get(eventName));
        }

        /**
         * Adds a stack trace event or changes an existing definition of a stack trace event with the same name in the
         * deployment definition
         * @param stackTraceEvent definition to be added to the deployment
         * @return the current builder instance
         */
        public Builder stackTraceEvent(StackTraceEvent stackTraceEvent) {
            addInternalEvent(stackTraceEvent);
            return this;
        }

        /**
         * A shortcut method to add a new stack trace event to the node definition
         * @param eventName the name of the stack trace event
         * @param stack the stack trace for the event which is a set of traces combined with comma where the last method
         *              call comes at the end of stack. For example, a.Class1.m1 calling b.Class2.m2 stack trace should be:
         *              "a.Class1.m1,b.Class2.m2"
         * @return the current builder instance
         */
        public Builder stackTrace(String eventName, String stack) {
            String[] stackParts = stack.trim().split(",");
            StackTraceEvent.Builder builder = this.withStackTraceEvent(eventName);
            for (String part: stackParts) {
                builder.trace(part);
            }

            return builder.and();
        }

        /**
         * A shortcut method to add a new stack trace event to the node definition
         * @param eventName the name of the stack trace event
         * @param stack the stack trace for the event which is a set of traces combined with comma where the last method
         *              call comes at the end of stack. For example, a.Class1.m1 calling b.Class2.m2 stack trace should be:
         *              "a.Class1.m1,b.Class2.m2"
         * @param blockAfter marks the event to be blocked after the last method call
         * @return the current builder instance
         */
        public Builder stackTrace(String eventName, String stack, boolean blockAfter) {
            String[] stackParts = stack.trim().split(",");
            StackTraceEvent.Builder builder = this.withStackTraceEvent(eventName);
            for (String part: stackParts) {
                builder.trace(part);
            }

            if (blockAfter) {
                builder.blockAfter();
            }

            return builder.and();
        }

        /**
         * Returns a scheduling event builder to define a new scheduling event object in the node definition
         * @param name of the scheduling event
         * @return a new scheduling event builder object initialized with the given name
         */
        public SchedulingEvent.Builder withSchedulingEvent(String name) {
            return new SchedulingEvent.Builder(this, name, this.name);
        }

        /**
         * Returns a scheduling event builder to change an existing scheduling event object in the deployment definition
         * with the given scheduling event name
         * @param eventName the scheduling event name to be changed through a scheduling event builder
         * @return A scheduling event builder instance already initialized with an existing scheduling event object in
         * the deployment definition
         * @throws DeploymentEntityNotFound if a scheduling event object with the given name is not present in the
         * deployment definition
         */
        public SchedulingEvent.Builder schedulingEvent(String eventName) {
            if (!internalEvents.containsKey(eventName) || !(internalEvents.get(eventName) instanceof SchedulingEvent)) {
                throw new DeploymentEntityNotFound(eventName, "SchedulingEvent");
            }
            return new SchedulingEvent.Builder(this,
                    (SchedulingEvent) internalEvents.get(eventName));
        }

        /**
         * Adds a scheduling event or changes an existing definition of a scheduling event with the same name in the
         * deployment definition
         * @param schedulingEvent definition to be added to the deployment
         * @return the current builder instance
         */
        public Builder schedulingEvent(SchedulingEvent schedulingEvent) {
            addInternalEvent(schedulingEvent);
            return this;
        }

        /**
         * A shortcut method to add a scheduling event to block before the stack trace of the given stack trace event name
         * @param eventName the name of scheduling event to be added
         * @param stackTraceEventName the stack trace event name to be used as the blocking point
         * @return the current builder instance
         */
        public Builder blockBefore(String eventName, String stackTraceEventName) {
            return withSchedulingEvent(eventName)
                    .operation(SchedulingOperation.BLOCK)
                    .before(stackTraceEventName).and();
        }

        /**
         * A shortcut method to add a scheduling event to block after the stack trace of the given stack trace event name
         * @param eventName the name of scheduling event to be added
         * @param stackTraceEventName the stack trace event name to be used as  the blocking point
         * @return the current builder instance
         */
        public Builder blockAfter(String eventName, String stackTraceEventName) {
            return withSchedulingEvent(eventName)
                    .operation(SchedulingOperation.BLOCK)
                    .after(stackTraceEventName).and();
        }

        /**
         * A shortcut method to add a scheduling event to unblock before the stack trace of the given stack trace event name
         * @param eventName the name of scheduling event to be added
         * @param stackTraceEventName the stack trace event name to be used as  the unblocking point
         * @return the current builder instance
         */
        public Builder unblockBefore(String eventName, String stackTraceEventName) {
            return withSchedulingEvent(eventName)
                    .operation(SchedulingOperation.UNBLOCK)
                    .before(stackTraceEventName).and();
        }

        /**
         * A shortcut method to add a scheduling event to unblock after the stack trace of the given stack trace event name
         * @param eventName the name of scheduling event to be added
         * @param stackTraceEventName the stack trace event name to be used as  the unblocking point
         * @return the current builder instance
         */
        public Builder unblockAfter(String eventName, String stackTraceEventName) {
            return withSchedulingEvent(eventName)
                    .operation(SchedulingOperation.UNBLOCK)
                    .after(stackTraceEventName).and();
        }

        /**
         * Returns a garbage collection event builder to define a new garbage collection event object in the node definition
         * @param name of the garbage collection event
         * @return a new garbage collection event builder object initialized with the given name
         */
        public GarbageCollectionEvent.Builder withGarbageCollectionEvent(String name) {
            return new GarbageCollectionEvent.Builder(this, name, this.name);
        }

        /**
         * Returns a garbage collection event builder to change an existing garbage collection event object in the deployment
         * definition with the given garbage collection event name
         * @param eventName the garbage collection event name to be changed through a garbage collection event builder
         * @return A garbage collection event builder instance already initialized with an existing garbage collection
         * event object in the deployment definition
         * @throws DeploymentEntityNotFound if a garbage collection event object with the given name is not present in the
         * deployment definition
         */
        public GarbageCollectionEvent.Builder garbageCollectionEvent(String eventName) {
            if (!internalEvents.containsKey(eventName) || !(internalEvents.get(eventName) instanceof GarbageCollectionEvent)) {
                throw new DeploymentEntityNotFound(eventName, "GarbageCollectionEvent");
            }
            return new GarbageCollectionEvent.Builder(this,
                    (GarbageCollectionEvent) internalEvents.get(eventName));
        }

        /**
         * Adds a garbage collection event or changes an existing definition of a garbage collection event with the same name in the
         * deployment definition
         * @param garbageCollectionEvent definition to be added to the deployment
         * @return the current builder instance
         */
        public Builder garbageCollectionEvent(GarbageCollectionEvent garbageCollectionEvent) {
            addInternalEvent(garbageCollectionEvent);
            return this;
        }

        /**
         * Returns a garbage collection event builder to change an existing garbage collection event object in the deployment definition
         * with the given garbage collection event name
         * @param eventName the garbage collection event name to be changed through a garbage collection event builder
         * @return A garbage collection event builder instance already initialized with an existing garbage collection event object in
         * the deployment definition
         * @throws DeploymentEntityNotFound if a garbage collection event object with the given name is not present in the
         * deployment definition
         */
        public Builder garbageCollection(String eventName) {
            return withGarbageCollectionEvent(eventName).and();
        }

        /**
         * A utility method to be used by concrete internal event creation methods to add an internal event to the node
         * @param event the event object to be added
         */
        private void addInternalEvent(InternalEvent event) {
            if (internalEvents.containsKey(event.getName())) {
                logger.warn("The internal event " + event.getName() + " is being redefined in the node "
                        + name + " definition!");
            }
            internalEvents.put(event.getName(), event);
        }

        /**
         * Mark the node to not be started in the startup
         * @return the current builder instance
         */
        public Builder offOnStartup() {
            this.offOnStartup = true;
            return this;
        }

        @Override
        public Node build() {
            return new Node(this);
        }
    }
}
