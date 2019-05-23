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

import io.failify.Constants;
import io.failify.FailifyRunner;
import io.failify.exceptions.DeploymentEntityNameConflictException;
import io.failify.exceptions.DeploymentEntityNotFound;
import io.failify.util.FileUtil;
import io.failify.dsl.DeploymentEntity;
import io.failify.dsl.events.TestCaseEvent;
import io.failify.dsl.events.internal.BlockingEvent;
import io.failify.dsl.events.internal.SchedulingEvent;
import io.failify.dsl.events.internal.SchedulingOperation;
import io.failify.dsl.ReferableDeploymentEntity;
import io.failify.dsl.events.InternalEvent;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

/**
 * This class defines a distributed system deployment architecture including services, nodes and shared directories.
 * Services are templates for creating a node. There is also a run sequence field which can be used to impose a specific
 * order between nodes in the runtime by combining a set of internal and test case events.
 */
public class Deployment extends DeploymentEntity {
    private static Logger logger = LoggerFactory.getLogger(Deployment.class);

    private final Map<String, Node> nodes; // map of node names to node objects
    private final Map<String, Service> services; // map of service names to service objects
    private final Set<String> sharedDirectories; // set of directories to be shared between all the nodes
    private final Map<String, TestCaseEvent> testCaseEvents; // map of test case event names to their objects
    private final Map<String, ReferableDeploymentEntity> referableDeploymentEntities; // map of referable deployment entities
    private final Map<String, DeploymentEntity> deploymentEntities; // map of all the deployment entities
    private final Map<String, BlockingEvent> blockingEvents; // map of blocking events
    private final Map<String, SchedulingEvent> blockingSchedulingEvents; // map of scheduling blocking events
    private final String runSequence;

    /**
     * Private Constructor
     * @param builder the builder instance to use for creating the class instance
     */
    private Deployment(Builder builder) {
        super(builder.getName());
        runSequence = builder.runSequence;
        nodes = Collections.unmodifiableMap(builder.nodes);
        services = Collections.unmodifiableMap(builder.services);
        sharedDirectories = Collections.unmodifiableSet(builder.sharedDorectories);
        testCaseEvents = Collections.unmodifiableMap(builder.testCaseEvents);
        deploymentEntities = Collections.unmodifiableMap(generateDeploymentEntitiesMap());
        // List of events that potentially can impose a blockage in the run sequence
        blockingEvents = Collections.unmodifiableMap(generateBlockingEventsMap());
        // List of blocking type scheduling events that are present in the run sequence
        blockingSchedulingEvents = Collections.unmodifiableMap(generateBlockingSchedulingEventsMap());
        referableDeploymentEntities = Collections.unmodifiableMap(generateReferableEntitiesMap());
    }

    /**
     * @param name the name of the deployment definition
     * @return given the deployment definition name, returns a deployment definition builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * @param instance an instance of the deployment definition to be changed using the builder
     * @return given the deployment definition instance, returns a deployment definition builder
     */
    public static Builder builder(Deployment instance) {
        return new Builder(instance);
    }

    public FailifyRunner start() {
        return FailifyRunner.run(this);
    }

    /**
     * Generates a map of entities that can be referred in the run sequence including internal and test case
     * events using the deployment definition
     * @return the map of referable entities
     * @throws DeploymentEntityNameConflictException if there is a name overlap
     */
    private Map<String,ReferableDeploymentEntity> generateReferableEntitiesMap() {
        Map<String, ReferableDeploymentEntity> returnMap = new HashMap<>();

        for (Node node: nodes.values()) {
            if (returnMap.containsKey(node.getName())) {
                throw new DeploymentEntityNameConflictException(node.getName());
            }
            returnMap.put(node.getName(), node);
            for (InternalEvent internalEvent: node.getInternalEvents().values()) {
                if (returnMap.containsKey(internalEvent.getName())) {
                    throw new DeploymentEntityNameConflictException(internalEvent.getName());
                }
                returnMap.put(internalEvent.getName(), internalEvent);
            }
        }

        for (TestCaseEvent testCaseEvent : testCaseEvents.values()) {
            if (returnMap.containsKey(testCaseEvent.getName())) {
                throw new DeploymentEntityNameConflictException(testCaseEvent.getName());
            }
            returnMap.put(testCaseEvent.getName(), testCaseEvent);
        }

        return returnMap;
    }

    /**
     * Generates a map of all the entities in the deployment definition
     * @return the map of all the entities
     * @throws DeploymentEntityNameConflictException if there is a name overlap
     */
    private Map<String, DeploymentEntity> generateDeploymentEntitiesMap() {
        Map<String, DeploymentEntity> returnMap = new HashMap<>(generateReferableEntitiesMap());

        for (Service service: services.values()) {
            if (returnMap.containsKey(service.getName())) {
                throw new DeploymentEntityNameConflictException(service.getName());
            }
            returnMap.put(service.getName(), service);
        }

        return returnMap;
    }

    /**
     * Generates a map of events that can be blocking including scheduling and stack trace events
     * @return the list of blocking events
     */
    private Map<String, BlockingEvent> generateBlockingEventsMap() {
        Map<String, BlockingEvent> returnList = new HashMap<>();

        for (Node node: nodes.values()) {
            for (InternalEvent internalEvent: node.getInternalEvents().values()) {
                if (internalEvent instanceof BlockingEvent) {
                    returnList.put(internalEvent.getName(), (BlockingEvent) internalEvent);
                }
            }
        }

        return returnList;
    }

    /**
     * Generates a map of scheduling events with the block operation
     * @return the list of blocking scheduling events
     */
    private Map<String, SchedulingEvent> generateBlockingSchedulingEventsMap() {
        Map<String, SchedulingEvent> returnList = new HashMap<>();

        for (Node node: nodes.values()) {
            for (InternalEvent internalEvent: node.getInternalEvents().values()) {
                if (internalEvent instanceof SchedulingEvent
                        && ((SchedulingEvent)internalEvent).getOperation() == SchedulingOperation.BLOCK
                        && runSequence.contains(internalEvent.getName())) {
                    returnList.put(internalEvent.getName(), (SchedulingEvent) internalEvent);
                }
            }
        }

        return returnList;
    }

    /**
     * @param name of the node
     * @return the node object for the given name
     */
    public Node getNode(String name) {
        return nodes.get(name);
    }

    /**
     * @param name of the service
     * @return the service object for the given name
     */
    public Service getService(String name) {
        return services.get(name);
    }

    public Boolean testCaseEventExists(String name) {
        return testCaseEvents.containsKey(name);
    }

    public Map<String, BlockingEvent> getBlockingEvents() {
        return blockingEvents;
    }

    /**
     * @param name of the blocking event
     * @return the blocking event object for the given name
     */
    public BlockingEvent getBlockingEvent(String name) {
        return blockingEvents.get(name);
    }

    public Map<String, SchedulingEvent> getBlockingSchedulingEvents() {
        return blockingSchedulingEvents;
    }

    public String getRunSequence() {
        return runSequence;
    }

    /**
     * @param name of the referable deployment entity
     * @return the referable deployment entity object for the given name
     */
    public ReferableDeploymentEntity getReferableDeploymentEntity(String name) {
        return referableDeploymentEntities.get(name);
    }

    /**
     * @param name of the deployment entity
     * @return the deployment entity object for the given name
     */
    public DeploymentEntity getDeploymentEntity(String name) {
        return deploymentEntities.get(name);
    }

    public Map<String, Node> getNodes() {
        return nodes;
    }

    public Map<String, Service> getServices() {
        return services;
    }

    public Set<String> getSharedDirectories() {
        return sharedDirectories;
    }

    public Map<String, ReferableDeploymentEntity> getReferableDeploymentEntities() {
        return referableDeploymentEntities;
    }

    public Map<String, DeploymentEntity> getDeploymentEntities() {
        return deploymentEntities;
    }

    // TODO make this more efficient and refactor other places that uses this functionality

    /**
     * Checks if the given event name is in the run sequence or not
     * @param eventName to be checked
     * @return true if the event is in the run sequence, otherwise false
     */
    public Boolean isInRunSequence(String eventName) {
        String[] eventNames = runSequence.split("\\W+");
        for (String event: eventNames) {
            if (event.equals(eventName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The builder class for the deployment definition
     */
    public static class Builder extends BuilderBase<Deployment, Builder> {
        private Map<String, Node> nodes;
        private String runSequence;
        private Map<String, Service> services;
        private Set<String> sharedDorectories;
        private Map<String, TestCaseEvent> testCaseEvents;

        /**
         * Constructor
         * @param name of the deployment definition which will be used in the workspace creation
         */
        public Builder(String name) {
            super(null, name);
            nodes = new HashMap<>();
            services = new HashMap<>();
            sharedDorectories = new HashSet<>();
            testCaseEvents = new HashMap<>();
            runSequence = "";
        }

        /**
         * Constructor
         * @param instance of a deployment that needs to be changed
         */
        public Builder(Deployment instance) {
            super(null, instance);
            nodes = new HashMap<>(instance.nodes);
            services = new HashMap<>(instance.services);
            sharedDorectories = new HashSet<>(instance.sharedDirectories);
            testCaseEvents = new HashMap<>(instance.testCaseEvents);
            runSequence =  new String(instance.runSequence);
        }

        /**
         * Adds a node or changes an existing definition of a node with the same name in the deployment definition
         * @param node definition to be added to the deployment
         * @return the current builder instance
         */
        public Builder node(Node node) {
            if (nodes.containsKey(node.getName())) {
                logger.warn("The node " + node.getName() + " is being redefined in the deployment definition!");
            }
            nodes.put(node.getName(), node);
            return this;
        }

        /**
         * Returns a node builder to change an existing node object in the deployment definition with the given node name
         * @param nodeName to be changed through a node builder
         * @return A node builder instance already initialized with an existing node object in the deployment definition
         * @throws DeploymentEntityNotFound if a node object with the given name is not present in the deployment definition
         */
        public Node.Builder node(String nodeName) {
            if (!nodes.containsKey(nodeName)) {
                throw new DeploymentEntityNotFound(nodeName, "Node");
            }
            return new Node.Builder(this, nodes.get(nodeName));
        }

        /**
         * Returns a node builder to define a new node object in the deployment definition
         * @param name of the node
         * @param serviceName for the node
         * @return a new node builder object initialized with the given arguments
         */
        public Node.Builder withNode(String name, String serviceName) {
            return new Node.Builder(this, name, serviceName);
        }

        /**
         * Defines a number of nodes based on the given service name and node name prefix. For example for 3, node, service1,
         * nodes node1, node2, node3 will be defined out of service1. Note that the node number start from 1.
         * @param numOfNodes the number of nodes to be defined
         * @param nodeNamePrefix the prefix to be used for node names
         * @param serviceName the service name for the defined nodes
         * @return
         */
        public Builder nodeInstances(int numOfNodes, String nodeNamePrefix, String serviceName, boolean offOnStartup) {
            for (int i=1; i<=numOfNodes; i++) {
                Node.Builder builder = withNode(nodeNamePrefix + i, serviceName);
                if (offOnStartup) builder.offOnStartup();
                builder.and();
            }
            return this;
        }

        /**
         * Adds a service or changes an existing definition of a service with the same name in the deployment definition
         * @param service definition to be added to the deployment
         * @return the current builder instance
         */
        public Builder service(Service service) {
            if (services.containsKey(service.getName())) {
                logger.warn("The service " + service.getName() + " is being redefined in the deployment definition!");
            }
            services.put(service.getName(), service);
            return this;
        }

        /**
         * Returns a service builder to change an existing service object in the deployment definition with the given
         * service name
         * @param serviceName to be changed through a service builder
         * @return A service builder instance already initialized with an existing service object in the deployment definition
         * @throws DeploymentEntityNotFound if a service object with the given name is not present in the deployment definition
         */
        public Service.Builder service(String serviceName) {
            if (!services.containsKey(serviceName)) {
                throw new DeploymentEntityNotFound(serviceName, "Service");
            }
            return new Service.Builder(this, services.get(serviceName));
        }

        /**
         * Returns a service builder to define a new service object in the deployment definition
         * @param name of the service
         * @return a new service builder object initialized with the given arguments
         */
        public Service.Builder withService(String name) {
            return new Service.Builder(this, name);
        }

        /**
         * Returns a service builder to define a new service object in the deployment definition based on an existing service
         * definition
         * @param name of the service
         * @param baseService the name of the base service to build upon
         * @return a new service builder object initialized with the given arguments
         */
        public Service.Builder withService(String name, String baseService) {
            return new Service.Builder(this, name, services.get(baseService));
        }

        /**
         * Returns a service builder initialized with the given name, application library paths from the current Java
         * class path and instrumentable paths based on the given instrumentable path patterns.
         * So, for example, for including commons-io lib jar file as an instrumentable path you can use "**commons-io*.jar"
         * instrumentable path pattern
         * Also, it adds an environment variable named FAILIFY_JVM_CLASSPATH to the service which should be included
         * in the java class path in the service or node start command.
         * @param name of the service
         * @param instrumentablePathPatterns the patterns to match against the paths in the current java class path to be
         *                                   added as an instrumentable path to the service
         * @return a service builder initialized with the given name, application library paths from the current Java class
         * path and instrumentable paths based on the given instrumentable path patterns to be configured further.
         */
        public Service.Builder withServiceFromJvmClasspath(String name, String... instrumentablePathPatterns) {
            Set<String> instrumentablePathSet = new HashSet<>();
            Service.Builder serviceBuilder = new Service.Builder(this, name);

            List<String> classPathList = Arrays.asList(System.getProperty("java.class.path").split(":"));

            for (String instrumentablePathPattern: instrumentablePathPatterns) {
                if (new File(instrumentablePathPattern).exists()) {
                    instrumentablePathSet.add(Paths.get(instrumentablePathPattern).toAbsolutePath().normalize().toString());
                } else {
                    try {
                        for (String instrumentablePath : FileUtil
                                .findAllMatchingPaths(instrumentablePathPattern, classPathList)) {
                            instrumentablePathSet.add(Paths.get(instrumentablePath).toAbsolutePath().normalize().toString());
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Error while trying to expand instrumentable path " + instrumentablePathPattern, e);
                    }
                }
            }

            StringJoiner newClassPath = new StringJoiner(":");
            for (String path: classPathList) {
                // target path should be in linux format, but path may come from windows
                String newTargetPath = "/" + path.replaceAll("\\W", "");
                if (path.endsWith(".jar")) {
                    newTargetPath = newTargetPath + ".jar";
                }

                if (instrumentablePathSet.contains(Paths.get(path).toAbsolutePath().normalize().toString())) {
                    serviceBuilder.appPath(path, newTargetPath, PathAttr.CHANGEABLE);
                    serviceBuilder.instrumentablePath(newTargetPath);
                } else {
                    serviceBuilder.appPath(path, newTargetPath, PathAttr.LIBRARY);
                }
                newClassPath.add(newTargetPath);
            }
            serviceBuilder.env(Constants.JVM_CLASSPATH_ENVVAR_NAME, newClassPath.toString());
            return serviceBuilder;
        }

        /**
         * Adds a shared directory which will be accessible to all the nodes through the given path
         * @param path the absolute target path of the shared directory inside the nodes
         * @return the current builder instance
         */
        public Builder sharedDir(String path) {
            if (!FileUtil.isPathAbsoluteInUnix(path)) {
                throw new RuntimeException("The shared directory `" + path + "` path is not absolute!");
            }

            sharedDorectories.add(FilenameUtils.normalizeNoEndSeparator(path, true));
            return this;
        }

        /**
         * Adds test case events that can be included in the run sequence and be enforced in the test case
         * @param events the name of the test case events
         * @return the current builder instance
         */
        public Builder testCaseEvents(String... events) {
            for (String event: events) {
                testCaseEvents.put(event.trim(), new TestCaseEvent(event.trim()));
            }
            return this;
        }

        /**
         * Adds a run sequence to the deployment definition
         * @param sequence the sequence of events to be enforced. Events can be joined using and(*), or(|) and parenthesis
         *                 "and" and "or" mean sequential and concurrent run respectively.
         * @return the current builder instance
         */
        public Builder runSeq(String sequence) {
            runSequence = sequence;
            return this;
        }

        public Deployment build() {
            return new Deployment(this);
        }

        @Override
        protected void returnToParent(Deployment builtObj) {
            return;
        }
    }
}
