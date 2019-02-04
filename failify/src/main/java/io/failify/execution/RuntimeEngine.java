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

package io.failify.execution;

import io.failify.FailifyRunner;
import io.failify.dsl.entities.Deployment;
import io.failify.dsl.entities.ExposedPortDefinition;
import io.failify.dsl.entities.Node;
import io.failify.dsl.entities.Service;
import io.failify.rt.Failify;
import io.failify.workspace.NodeWorkspace;
import io.failify.Constants;
import io.failify.dsl.events.ExternalEvent;
import io.failify.exceptions.RuntimeEngineException;
import io.failify.execution.single_node.SingleNodeRuntimeEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeoutException;

public abstract class RuntimeEngine implements LimitedRuntimeEngine {
    private final static Logger logger = LoggerFactory.getLogger(RuntimeEngine.class);
    private final EventServer eventServer;
    protected final Deployment deployment;
    protected Map<String, NodeWorkspace> nodeWorkspaceMap;
    protected boolean stopped;

    public RuntimeEngine(Deployment deployment, Map<String, NodeWorkspace> nodeWorkspaceMap) {
        this.stopped = true;
        this.deployment = deployment;
        this.nodeWorkspaceMap = nodeWorkspaceMap;
        eventServer = new EventServer();
        EventService.initialize(deployment);
    }

    // TODO this method should use an external configuration to detect the proper runtime engine and its corresponding configs
    // By defualt this methos returns single node runtime engine
    public static RuntimeEngine getRuntimeEngine(Deployment deployment, Map<String, NodeWorkspace> nodeWorkspaceMap) {
        return new SingleNodeRuntimeEngine(deployment, nodeWorkspaceMap);
    }

    public Set<String> nodeNames() {
        return deployment.getNodes().keySet();
    }

    public boolean isStopped() {
        return stopped;
    }

    public void start(FailifyRunner failifyRunner) throws RuntimeEngineException {
        // Exits if nodes' workspaces is not set
        if (nodeWorkspaceMap == null || nodeWorkspaceMap.isEmpty()) {
            throw new RuntimeEngineException("NodeWorkspaces is not set!");
        }

        if (!deployment.getSharedDirectories().isEmpty()) {
            logger.info("Starting file sharing service ...");
            startFileSharingService();
        }

        logger.info("Starting event server ...");
        startEventServer();

        // Configure local Failify runtime
        Failify.configure("127.0.0.1", String.valueOf(eventServer.getPortNumber()));

        logger.info("Starting external events ...");
        startExternalEvents(failifyRunner);

        try {
            logger.info("Starting nodes ...");
            stopped = false;
            startNodes();
        } catch (RuntimeEngineException e) {
            stop(true, 0);
            throw e;
        }
    }

    protected void startExternalEvents(FailifyRunner failifyRunner) {
        // Find those external events that are present in the run sequence
        List<ExternalEvent> externalEvents = new ArrayList<>();
        for (String id: deployment.getRunSequence().split("\\W+")) {
            if (deployment.getExternalEvent(id) != null) {
                externalEvents.add(deployment.getExternalEvent(id));
            }
        }

        for (ExternalEvent externalEvent: externalEvents) {
            externalEvent.start(failifyRunner);
        }
    }

    protected void startEventServer() throws RuntimeEngineException {
        eventServer.start();
    }

    public void stop(boolean kill, Integer secondsUntilForcedStop) {
        logger.info("Stopping the runtime engine ...");
        logger.info("Stopping external events ...");
        stopExternalEvents();
        logger.info("Stopping nodes ...");
        stopNodes(kill, secondsUntilForcedStop);
        logger.info("Stopping event server ...");
        stopEventServer();
        if (!deployment.getSharedDirectories().isEmpty()) {
            logger.info("Stopping file sharing service ...");
            stopFileSharingService();
        }
        stopped = true;
    }

    protected void stopExternalEvents() {
        for (ExternalEvent externalEvent: deployment.getExternalEvents().values()) {
            externalEvent.stop();
        }
    }

    protected void stopEventServer() {
        eventServer.stop();
    }

    protected Map<String, String> getNodeEnvironmentVariablesMap(String nodeName, Map<String, String> environment) {
        Node node = deployment.getNode(nodeName);
        Service nodeService = deployment.getService(node.getServiceName());

        for (Map.Entry<String, String> entry: nodeService.getEnvironmentVariables().entrySet()) {
            environment.put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, String> entry: node.getEnvironmentVariables().entrySet()) {
            environment.put(entry.getKey(), entry.getValue());
        }

        return environment;
    }

    protected Map<String, String> improveEnvironmentVariablesMap(String nodeName, Map<String, String> environment)
            throws RuntimeEngineException {
        environment.put(Constants.FAILIFY_EVENT_SERVER_IP_ADDRESS_ENV_VAR, getEventServerIpAddress());
        environment.put(Constants.FAILIFY_EVENT_SERVER_PORT_NUMBER_ENV_VAR, String.valueOf(eventServer.getPortNumber()));
        return environment;
    }

    protected final Map<String, String> getNodeEnvironmentVariablesMap(String nodeName) throws RuntimeEngineException {
        Map<String, String> retMap = new HashMap<>();
        retMap = getNodeEnvironmentVariablesMap(nodeName, retMap);
        retMap = improveEnvironmentVariablesMapForEngine(nodeName, retMap);
        retMap = improveEnvironmentVariablesMap(nodeName, retMap);
        return retMap;
    }

    protected Set<ExposedPortDefinition> getNodeExposedPorts(String nodeName) {
        Node node = deployment.getNode(nodeName);
        Service nodeService = deployment.getService(node.getServiceName());

        Set<ExposedPortDefinition> ports = new HashSet<>(nodeService.getExposedPorts());
        ports.addAll(node.getExposedPorts());
        return ports;
    }

    protected String getNodeInitCommand(String nodeName) {
        Node node = deployment.getNode(nodeName);
        Service nodeService = deployment.getService(node.getServiceName());

        if (node.getInitCommand() != null) {
            return node.getInitCommand();
        }
        return nodeService.getInitCommand();
    }

    protected String getNodeStartCommand(String nodeName) {
        Node node = deployment.getNode(nodeName);
        Service nodeService = deployment.getService(node.getServiceName());

        if (node.getStartCommand() != null) {
            return node.getStartCommand();
        }
        return nodeService.getStartCommand();
    }

    protected String getNodeStopCommand(String nodeName) {
        Node node = deployment.getNode(nodeName);
        Service nodeService = deployment.getService(node.getServiceName());

        if (node.getStopCommand() != null) {
            return node.getStopCommand();
        }
        return nodeService.getStopCommand();
    }

    @Override
    public void waitFor(String eventName) throws RuntimeEngineException {
        try {
            waitFor(eventName, false, null);
        } catch (TimeoutException e) {
            // never happens here
        }
    }

    @Override
    public void waitFor(String eventName, Boolean includeEvent) throws RuntimeEngineException {
        try {
            waitFor(eventName, false, null);
        } catch (TimeoutException e) {
            // never happens here
        }
    }

    @Override
    public void waitFor(String eventName, Integer timeout) throws RuntimeEngineException, TimeoutException {
        waitFor(eventName, false, timeout);
    }

    @Override
    public void waitFor(String eventName, Boolean includeEvent, Integer timeout)
            throws RuntimeEngineException, TimeoutException {
        if (deployment.isInRunSequence(eventName)) {
            logger.info("Waiting for event {} in workload ...", eventName);
            try {
                Failify.getInstance().blockAndPoll(eventName, includeEvent, timeout);
            } catch (TimeoutException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeEngineException("Error happened while waiting for event " + eventName, e);
            }
        } else {
            throw new RuntimeEngineException("Event " + eventName + " is not referred to in the run sequence. Thus," +
                    " its order cannot be enforced!");
        }
    }

    private void sendEvent(String eventName) throws RuntimeEngineException {
        if (deployment.isInRunSequence(eventName)) {
            logger.info("Sending workload event {} ...", eventName);
            Failify.getInstance().allowBlocking();
            Failify.getInstance().enforceOrder(eventName, null);
        } else {
            throw new RuntimeEngineException("Event " + eventName + " is not referred to" +
                    " in the run sequence. Thus, its order cannot be sent from the workload!");
        }
    }

    @Override
    public void enforceOrder(String eventName, FailifyCheckedRunnable action) throws RuntimeEngineException {
        try {
            enforceOrder(eventName, action, null);
        } catch (TimeoutException e) {
            // never happens here
        }
    }

    @Override
    public void enforceOrder(String eventName, FailifyCheckedRunnable action, Integer timeout)
            throws RuntimeEngineException, TimeoutException {

        if (!deployment.workloadEventExists(eventName)) {
            throw new RuntimeEngineException("Event " + eventName + " is not a defined workload event and cannot be"
                    + " enforced using this method!");
        }

        waitFor(eventName, false, timeout);
        if (action != null) {
            action.run();
        }
        sendEvent(eventName);
    }

    /**
     * This method improves a node's env var map
     * @param nodeName the corresponding node to be improved
     * @param environment the current environment of the node
     * @return the improved environment for the node
     * @throws RuntimeEngineException if something goes wrong
     */
    protected abstract Map<String, String> improveEnvironmentVariablesMapForEngine(String nodeName, Map<String, String> environment)
            throws RuntimeEngineException;
    /**
     * This method should find the best IP address for the nodes to connect to the event server based on the current environment
     * @return the IP address to connect to the event server
     * @throws RuntimeEngineException if something goes wrong
     */
    protected abstract String getEventServerIpAddress() throws RuntimeEngineException;
    /**
     * This method should start all of the nodes. In case of a problem in startup of a node, all of the started nodes should be
     * stopped and a RuntimeEngine Exception should be thrown
     * @throws RuntimeEngineException
     */
    protected abstract void startNodes() throws RuntimeEngineException;

    /**
     * This method should stop all of the nodes and in case of a failure in stopping something it won't throw any exception, but
     * error logs the exception or a message. This method should only be called when stopping the runtime engine
     */
    protected abstract void stopNodes(Boolean kill, Integer secondsUntilForcedStop);

    /**
     * This method should start the file sharing service (if any), create the defined shared directory in the deployment definition
     * if they do not exist, and make them available through the sharing service. Mounting in the nodes (if necessary) should be
     * done later when starting the nodes.
     * @throws RuntimeEngineException if some error happens when creating the shared directory for the nodes
     */
    protected abstract void startFileSharingService() throws RuntimeEngineException;

    /**
     * This method should stop the potentially running file sharing server and unmount shared directories in the nodes (if necessary).
     * In case of a failure in stopping something it won't throw any exception, but error logs the exception or a message.
     * This method should only be called when stopping the runtime engine
     */
    protected abstract void stopFileSharingService();
}
