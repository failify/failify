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

import me.arminb.spidersilk.dsl.DeploymentEntity;
import me.arminb.spidersilk.exceptions.DeploymentEntityNameConflictException;
import me.arminb.spidersilk.dsl.ReferableDeploymentEntity;
import me.arminb.spidersilk.dsl.events.ExternalEvent;
import me.arminb.spidersilk.dsl.events.InternalEvent;
import me.arminb.spidersilk.dsl.events.external.NodeOperationEvent;
import me.arminb.spidersilk.dsl.events.external.Workload;

import java.util.*;

/**
 * The container class for the whole distributed system deployment definition. The builder class is the entry point for building
 * the deployment definition
 */
public class Deployment extends DeploymentEntity {
    private final Map<String, Node> nodes;
    private final Map<String, Service> services;
    private final Map<String, ExternalEvent> executableEntities;
    private final Map<String, ReferableDeploymentEntity> referableDeploymentEntities;
    private final Map<String, DeploymentEntity> deploymentEntities;
    private final Integer eventServerPortNumber;
    private final String runSequence;

    private Deployment(DeploymentBuilder builder) {
        super("deployment");
        nodes = Collections.unmodifiableMap(builder.nodes);
        services = Collections.unmodifiableMap(builder.services);
        executableEntities = Collections.unmodifiableMap(builder.executableEntities);
        deploymentEntities = Collections.unmodifiableMap(generateDeploymentEntitiesMap());
        referableDeploymentEntities = Collections.unmodifiableMap(generateReferableEntitiesMap());
        runSequence = builder.runSequence;
        eventServerPortNumber = new Integer(builder.eventServerPortNumber);
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

        for (ExternalEvent externalEvent: executableEntities.values()) {
            if (returnMap.containsKey(externalEvent.getName())) {
                throw new DeploymentEntityNameConflictException(externalEvent.getName());
            }
            returnMap.put(externalEvent.getName(), externalEvent);
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

    public Node getNode(String name) {
        return nodes.get(name);
    }

    public Service getService(String name) {
        return services.get(name);
    }

    public ExternalEvent getExecutableEntity(String name) {
        return executableEntities.get(name);
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

    public Map<String, ExternalEvent> getExecutableEntities() {
        return executableEntities;
    }

    public Map<String, ReferableDeploymentEntity> getReferableDeploymentEntities() {
        return referableDeploymentEntities;
    }

    public Map<String, DeploymentEntity> getDeploymentEntities() {
        return deploymentEntities;
    }

    public String getEventServerPortNumber() {
        return eventServerPortNumber.toString();
    }

    public static class DeploymentBuilder extends DeploymentEntity.DeploymentBuilderBase<Deployment, DeploymentEntity.DeploymentBuilderBase> {
        private Map<String, Node> nodes;
        private String runSequence;
        private Map<String, Service> services;
        private Map<String, ExternalEvent> executableEntities;
        private Integer eventServerPortNumber;


        public DeploymentBuilder() {
            super("root");
            nodes = new HashMap<>();
            services = new HashMap<>();
            executableEntities = new HashMap<>();
            runSequence = "";
            eventServerPortNumber = 8765; // Default port number for the event server
        }

        public DeploymentBuilder(Deployment instance) {
            super(instance);
            nodes = new HashMap<>(instance.nodes);
            services = new HashMap<>(instance.services);
            executableEntities = new HashMap<>(instance.executableEntities);
            runSequence =  new String(instance.runSequence);
            eventServerPortNumber = new Integer(instance.eventServerPortNumber);
        }

        public DeploymentBuilder node(Node node) {
            if (nodes.containsKey(node.getName())) {
                throw new DeploymentEntityNameConflictException(node.getName());
            }
            nodes.put(node.getName(), node);
            return this;
        }

        public Node.NodeBuilder withNode(String name, String serviceName) {
            return new Node.NodeBuilder(this, name, serviceName);
        }

        public DeploymentBuilder service(Service service) {
            if (services.containsKey(service.getName())) {
                throw new DeploymentEntityNameConflictException(service.getName());
            }
            services.put(service.getName(), service);
            return this;
        }

        public Service.ServiceBuilder withService(String name) {
            return new Service.ServiceBuilder(this, name);
        }

        public DeploymentBuilder workload(Workload workload) {
            if (executableEntities.containsKey(workload.getName())) {
                throw new DeploymentEntityNameConflictException(workload.getName());
            }
            executableEntities.put(workload.getName(), workload);
            return this;
        }

        public Workload.WorkloadBuilder withWorkload(String name) {
            return new Workload.WorkloadBuilder(this, name);
        }

        public NodeOperationEvent.NodeOperationEventBuilder withNodeOperationEvent(String name) {
            return new NodeOperationEvent.NodeOperationEventBuilder(this, name);
        }

        public DeploymentBuilder nodeOperationEvent(NodeOperationEvent nodeOperationEvent) {
            if (executableEntities.containsKey(nodeOperationEvent.getName())) {
                throw new DeploymentEntityNameConflictException(nodeOperationEvent.getName());
            }
            executableEntities.put(nodeOperationEvent.getName(), nodeOperationEvent);
            return this;
        }

        public DeploymentBuilder eventServerPortNumber(Integer eventServerPortNumber) {
            this.eventServerPortNumber = eventServerPortNumber;
            return this;
        }

        public DeploymentBuilder runSequence(String sequence) {
            runSequence = sequence;
            return this;
        }

        public Deployment build() {
            /**
             * TODO deployment definition verification
             * including required fields,correct service references in nodes definition and run sequence correctness
             */
            return new Deployment(this);
        }

        @Override
        protected void returnToParent(Deployment builtObj) {
            return;
        }
    }
}
