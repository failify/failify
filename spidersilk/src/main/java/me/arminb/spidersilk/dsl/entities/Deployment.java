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

import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.dsl.DeploymentEntity;
import me.arminb.spidersilk.dsl.events.WorkloadEvent;
import me.arminb.spidersilk.dsl.events.external.NetworkOperation;
import me.arminb.spidersilk.dsl.events.external.NetworkOperationEvent;
import me.arminb.spidersilk.dsl.events.external.NodeOperation;
import me.arminb.spidersilk.dsl.events.internal.BlockingEvent;
import me.arminb.spidersilk.dsl.events.internal.SchedulingEvent;
import me.arminb.spidersilk.dsl.events.internal.SchedulingOperation;
import me.arminb.spidersilk.exceptions.DeploymentEntityNameConflictException;
import me.arminb.spidersilk.dsl.ReferableDeploymentEntity;
import me.arminb.spidersilk.dsl.events.ExternalEvent;
import me.arminb.spidersilk.dsl.events.InternalEvent;
import me.arminb.spidersilk.dsl.events.external.NodeOperationEvent;
import me.arminb.spidersilk.exceptions.DeploymentEntityNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The container class for the whole distributed system deployment definition. The builder class is the entry point for building
 * the deployment definition
 */
public class Deployment extends DeploymentEntity {
    private static Logger logger = LoggerFactory.getLogger(Deployment.class);

    private final Map<String, Node> nodes;
    private final Map<String, Service> services;
    private final Set<String> sharedDirectories;
    private final Map<String, ExternalEvent> externalEvents;
    private final Map<String, WorkloadEvent> workloadEvents;
    private final Map<String, ReferableDeploymentEntity> referableDeploymentEntities;
    private final Map<String, DeploymentEntity> deploymentEntities;
    private final Map<String, BlockingEvent> blockingEvents;
    private final Map<String, SchedulingEvent> blockingSchedulingEvents;
    private final Integer eventServerPortNumber;
    private final Integer secondsToWaitForCompletion;
    private final String runSequence;
    private final String appHomeEnvVar;
    private final Integer secondsUntilForcedStop;
    private final Integer nextEventReceiptTimeout;

    private Deployment(DeploymentBuilder builder) {
        super(builder.getName());
        runSequence = builder.runSequence;
        appHomeEnvVar = builder.appHomeEnvVar;
        eventServerPortNumber = new Integer(builder.eventServerPortNumber);
        secondsToWaitForCompletion = new Integer(builder.secondsToWaitForCompletion);
        nodes = Collections.unmodifiableMap(builder.nodes);
        services = Collections.unmodifiableMap(builder.services);
        sharedDirectories = Collections.unmodifiableSet(builder.sharedDorectories);
        externalEvents = Collections.unmodifiableMap(builder.externalEvents);
        workloadEvents = Collections.unmodifiableMap(builder.workloadEvents);
        deploymentEntities = Collections.unmodifiableMap(generateDeploymentEntitiesMap());
        // List of events that potentially can impose a blockage in the run sequence
        blockingEvents = Collections.unmodifiableMap(generateBlockingEventsMap());
        // List of blocking type scheduling events that are present in the run sequence
        blockingSchedulingEvents = Collections.unmodifiableMap(generateBlockingSchedulingEventsMap());
        referableDeploymentEntities = Collections.unmodifiableMap(generateReferableEntitiesMap());
        secondsUntilForcedStop = builder.secondsUntilForcedStop;
        nextEventReceiptTimeout = builder.nextEventReceiptTimeout;
    }

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

        for (ExternalEvent externalEvent: externalEvents.values()) {
            if (returnMap.containsKey(externalEvent.getName())) {
                throw new DeploymentEntityNameConflictException(externalEvent.getName());
            }
            returnMap.put(externalEvent.getName(), externalEvent);
        }

        for (WorkloadEvent workloadEvent: workloadEvents.values()) {
            if (returnMap.containsKey(workloadEvent.getName())) {
                throw new DeploymentEntityNameConflictException(workloadEvent.getName());
            }
            returnMap.put(workloadEvent.getName(), workloadEvent);
        }

        return returnMap;
    }

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

    public Node getNode(String name) {
        return nodes.get(name);
    }

    public Service getService(String name) {
        return services.get(name);
    }

    public ExternalEvent getExternalEvent(String name) {
        return externalEvents.get(name);
    }

    public Boolean workloadEventExists(String name) {
        return workloadEvents.containsKey(name);
    }

    public Map<String, BlockingEvent> getBlockingEvents() {
        return blockingEvents;
    }

    public BlockingEvent getBlockingEvent(String name) {
        return blockingEvents.get(name);
    }

    public Map<String, SchedulingEvent> getBlockingSchedulingEvents() {
        return blockingSchedulingEvents;
    }

    public String getRunSequence() {
        return runSequence;
    }

    public ReferableDeploymentEntity getReferableDeploymentEntity(String name) {
        return referableDeploymentEntities.get(name);
    }

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

    public Map<String, ExternalEvent> getExternalEvents() {
        return externalEvents;
    }

    public Map<String, ReferableDeploymentEntity> getReferableDeploymentEntities() {
        return referableDeploymentEntities;
    }

    public Map<String, DeploymentEntity> getDeploymentEntities() {
        return deploymentEntities;
    }

    public Integer getEventServerPortNumber() {
        return eventServerPortNumber;
    }

    public Integer getSecondsToWaitForCompletion() {
        return secondsToWaitForCompletion;
    }

    public Integer getSecondsUntilForcedStop() {
        return secondsUntilForcedStop;
    }

    public Integer getNextEventReceiptTimeout() {
        return nextEventReceiptTimeout;
    }

    public String getAppHomeEnvVar() {
        return appHomeEnvVar;
    }

    // TODO make this more efficient and refactor other places that uses this functionality
    public Boolean isInRunSequence(String eventName) {
        String[] eventNames = runSequence.split("\\W+");
        for (String event: eventNames) {
            if (event.equals(eventName)) {
                return true;
            }
        }
        return false;
    }

    public static class DeploymentBuilder extends DeploymentEntity.DeploymentBuilderBase<Deployment, DeploymentEntity.DeploymentBuilderBase> {
        private Map<String, Node> nodes;
        private String runSequence;
        private Map<String, Service> services;
        private Set<String> sharedDorectories;
        private Map<String, WorkloadEvent> workloadEvents;
        private Map<String, ExternalEvent> externalEvents;
        private Integer eventServerPortNumber;
        private Integer secondsToWaitForCompletion;
        private String appHomeEnvVar;
        private Integer secondsUntilForcedStop;
        private Integer nextEventReceiptTimeout;

        public DeploymentBuilder(String name) {
            super(null, name);
            nodes = new HashMap<>();
            services = new HashMap<>();
            sharedDorectories = new HashSet<>();
            externalEvents = new HashMap<>();
            workloadEvents = new HashMap<>();
            runSequence = "";
            eventServerPortNumber = 8765; // Default port number for the event server
            secondsToWaitForCompletion = 5;
            appHomeEnvVar = Constants.DEFAULT_APP_HOME_ENVVAR_NAME;
            secondsUntilForcedStop = Constants.DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_STOP;
            nextEventReceiptTimeout = null;
        }

        public DeploymentBuilder(Deployment instance) {
            super(null, instance);
            nodes = new HashMap<>(instance.nodes);
            services = new HashMap<>(instance.services);
            sharedDorectories = new HashSet<>(instance.sharedDirectories);
            externalEvents = new HashMap<>(instance.externalEvents);
            workloadEvents = new HashMap<>(instance.workloadEvents);
            runSequence =  new String(instance.runSequence);
            eventServerPortNumber = new Integer(instance.eventServerPortNumber);
            secondsToWaitForCompletion = new Integer(instance.secondsToWaitForCompletion);
            appHomeEnvVar = new String(instance.appHomeEnvVar);
            secondsUntilForcedStop = new Integer(instance.secondsUntilForcedStop);
            nextEventReceiptTimeout = new Integer(instance.nextEventReceiptTimeout);
        }

        public DeploymentBuilder node(Node node) {
            if (nodes.containsKey(node.getName())) {
                logger.warn("The node " + node.getName() + " is being redefined in the deployment definition!");
            }
            nodes.put(node.getName(), node);
            return this;
        }

        public Node.NodeBuilder node(String nodeName) {
            if (!nodes.containsKey(nodeName)) {
                throw new DeploymentEntityNotFound(nodeName, "Node");
            }
            return new Node.NodeBuilder(this, nodes.get(nodeName));
        }

        public Node.NodeBuilder withNode(String name, String serviceName) {
            return new Node.NodeBuilder(this, name, serviceName);
        }

        public DeploymentBuilder service(Service service) {
            if (services.containsKey(service.getName())) {
                logger.warn("The service " + service.getName() + " is being redefined in the deployment definition!");
            }
            services.put(service.getName(), service);
            return this;
        }

        public Service.ServiceBuilder service(String serviceName) {
            if (!services.containsKey(serviceName)) {
                throw new DeploymentEntityNotFound(serviceName, "Service");
            }
            return new Service.ServiceBuilder(this, services.get(serviceName));
        }

        public Service.ServiceBuilder withService(String name) {
            return new Service.ServiceBuilder(this, name);
        }

        public Service.ServiceBuilder withServiceFromJavaClasspath(String name, String... pathToBeCopiedOver) {
            Service.ServiceBuilder serviceBuilder = new Service.ServiceBuilder(this, name);
            Set<String> copiedOverPaths = Arrays.stream(pathToBeCopiedOver)
                    .map((path) -> Paths.get(path).toAbsolutePath().normalize().toString())
                    .collect(Collectors.toSet());

            StringBuilder newClassPath = new StringBuilder();
            for (String path: System.getProperty("java.class.path").split(":")) {
                if (copiedOverPaths.contains(Paths.get(path).toAbsolutePath().normalize().toString())) {
                    String fileName = new File(path).getName();
                    // TODO this is not a good for handling directories. e.g. target/classes changes to lib/classes
                    serviceBuilder.applicationPath(path, "lib/" + fileName, true, true);
                    newClassPath.append("{{APP_HOME}}/lib/" + fileName);
                    newClassPath.append(":");
                } else {
                    serviceBuilder.applicationPath(path, true);
                    newClassPath.append(path);
                    newClassPath.append(":");
                }
            }
            serviceBuilder.environmentVariable(Constants.JAVA_CLASSPATH_ENVVAR_NAME, newClassPath.toString());
            serviceBuilder.serviceType(ServiceType.JAVA);
            return serviceBuilder;
        }

        public DeploymentBuilder sharedDirectory(String path) {
            sharedDorectories.add(Paths.get(path).toAbsolutePath().normalize().toString());
            return this;
        }

        public NodeOperationEvent.NodeOperationEventBuilder withNodeOperationEvent(String name) {
            return new NodeOperationEvent.NodeOperationEventBuilder(this, name);
        }

        public DeploymentBuilder workloadEvents(String events) {
            for (String event: events.trim().split(",")) {
                workloadEvents.put(event.trim(), new WorkloadEvent(event.trim()));
            }
            return this;
        }

        public NodeOperationEvent.NodeOperationEventBuilder nodeOperationEvent(String eventName) {
            if (!externalEvents.containsKey(eventName) || !(externalEvents.get(eventName) instanceof NodeOperationEvent)) {
                throw new DeploymentEntityNotFound(eventName, "NodeOperationEvent");
            }
            return new NodeOperationEvent.NodeOperationEventBuilder(this,
                    (NodeOperationEvent) externalEvents.get(eventName));
        }

        public DeploymentBuilder nodeOperationEvent(NodeOperationEvent nodeOperationEvent) {
            if (externalEvents.containsKey(nodeOperationEvent.getName())) {
                logger.warn("The node operation event " + nodeOperationEvent.getName() + " is being redefined in the deployment definition!");
            }
            externalEvents.put(nodeOperationEvent.getName(), nodeOperationEvent);
            return this;
        }

        public DeploymentBuilder startNode(String eventName, String nodeName) {
            return this.withNodeOperationEvent(eventName)
                    .nodeOperation(NodeOperation.START)
                    .nodeName(nodeName).and();
        }

        public DeploymentBuilder stopNode(String eventName, String nodeName) {
            return this.withNodeOperationEvent(eventName)
                    .nodeOperation(NodeOperation.STOP)
                    .nodeName(nodeName).and();
        }

        public DeploymentBuilder killNode(String eventName, String nodeName) {
            return this.withNodeOperationEvent(eventName)
                    .nodeOperation(NodeOperation.KILL)
                    .nodeName(nodeName).and();
        }

        public DeploymentBuilder restartNode(String eventName, String nodeName) {
            return this.withNodeOperationEvent(eventName)
                    .nodeOperation(NodeOperation.RESET)
                    .nodeName(nodeName).and();
        }

        public NetworkOperationEvent.NetworkOperationEventBuilder withNetworkOperationEvent(String name) {
            return new NetworkOperationEvent.NetworkOperationEventBuilder(this, name);
        }

        public NetworkOperationEvent.NetworkOperationEventBuilder networkOperationEvent(String eventName) {
            if (!externalEvents.containsKey(eventName) || !(externalEvents.get(eventName) instanceof NetworkOperationEvent)) {
                throw new DeploymentEntityNotFound(eventName, "NetworkOperationEvent");
            }
            return new NetworkOperationEvent.NetworkOperationEventBuilder(this,
                    (NetworkOperationEvent) externalEvents.get(eventName));
        }

        public DeploymentBuilder networkOperationEvent(NetworkOperationEvent networkOperationEvent) {
            if (externalEvents.containsKey(networkOperationEvent.getName())) {
                logger.warn("The network operation event " + networkOperationEvent.getName()
                        + " is being redefined in the deployment definition!");
            }
            externalEvents.put(networkOperationEvent.getName(), networkOperationEvent);
            return this;
        }

        public DeploymentBuilder networkPartition(String eventName, String nodePartitions) {
            return withNetworkOperationEvent(eventName)
                    .networkOperation(NetworkOperation.PARTITION)
                    .nodePartitions(nodePartitions).and();
        }

        public DeploymentBuilder removeNetworkPartition(String eventName) {
            return withNetworkOperationEvent(eventName)
                    .networkOperation(NetworkOperation.REMOVE_PARTITION).and();
        }

        public DeploymentBuilder linkDown(String eventName, String node1, String node2) {
            return withNetworkOperationEvent(eventName)
                    .networkOperation(NetworkOperation.LINK_DOWN)
                    .nodePartitions(node1 + "," + node2).and();
        }

        public DeploymentBuilder linkUp(String eventName, String node1, String node2) {
            return withNetworkOperationEvent(eventName)
                    .networkOperation(NetworkOperation.LINK_UP)
                    .nodePartitions(node1 + "," + node2).and();
        }

        public DeploymentBuilder eventServerPortNumber(Integer eventServerPortNumber) {
            this.eventServerPortNumber = eventServerPortNumber;
            return this;
        }

        public DeploymentBuilder runSequence(String sequence) {
            runSequence = sequence;
            return this;
        }

        public DeploymentBuilder exposeAppHomeDirectoryAs(String appHomeEnvVar) {
            this.appHomeEnvVar = appHomeEnvVar;
            return this;
        }

        public DeploymentBuilder secondsToWaitForCompletion(Integer secondsToWaitForCompletion) {
            this.secondsToWaitForCompletion = secondsToWaitForCompletion;
            return this;
        }

        public DeploymentBuilder secondsUntilForcedStop(Integer secondsUntilForcedStop) {
            this.secondsUntilForcedStop = secondsUntilForcedStop;
            return this;
        }

        public DeploymentBuilder nextEventReceiptTimeout(Integer nextEventReceiptTimeout) {
            this.nextEventReceiptTimeout = nextEventReceiptTimeout;
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
