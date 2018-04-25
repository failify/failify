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

package me.arminb.spidersilk.execution;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.entities.Service;
import me.arminb.spidersilk.dsl.events.ExternalEvent;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.rt.SpiderSilk;
import me.arminb.spidersilk.util.HostUtil;
import me.arminb.spidersilk.workspace.NodeWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.util.*;

public abstract class RuntimeEngine {
    private final static Logger logger = LoggerFactory.getLogger(RuntimeEngine.class);
    private final EventServer eventServer;
    protected final Deployment deployment;
    protected Map<String, NodeWorkspace> nodeWorkspaceMap;
    protected boolean stopped;

    public RuntimeEngine(Deployment deployment) {
        this.stopped = true;
        this.deployment = deployment;
        eventServer = new EventServer(deployment);
    }

    public boolean isStopped() {
        return stopped;
    }

    public void start() throws RuntimeEngineException {
        // Configure local SpiderSilk runtime
        try {
            SpiderSilk.configure(HostUtil.getLocalIpAddress(), deployment.getEventServerPortNumber().toString());
        } catch (UnknownHostException e) {
            throw new RuntimeEngineException("Cannot get the local IP address to configure the local SpiderSilk runtime!");
        }

        logger.info("Starting event server ...");
        startEventServer();
        logger.info("Starting external events ...");
        startExternalEvents();

        try {
            logger.info("Starting nodes ...");
            startNodes();
        } catch (RuntimeEngineException e) {
            stop();
            throw e;
        }

        // initialize event service. We do it here because we want the eligible blocking events to be satisfied after the nodes are up
        EventService.initialize(deployment);

        stopped = false;
    }

    protected void startExternalEvents() {
        // Find those external events that are present in the run sequence
        List<ExternalEvent> externalEvents = new ArrayList<>();
        for (String id: deployment.getRunSequence().split("\\W+")) {
            if (deployment.getExternalEvent(id) != null) {
                externalEvents.add(deployment.getExternalEvent(id));
            }
        }

        // TODO provide a handler for exception in threads and stop the runtime engine accordingly
        for (ExternalEvent externalEvent: externalEvents) {
            externalEvent.start(this);
        }
    }

    protected void startEventServer() throws RuntimeEngineException {
        eventServer.start();
    }

    public void stop() {
        logger.info("Stopping external events ...");
        stopExternalEvents();
        logger.info("Stopping nodes ...");
        stopNodes();
        logger.info("Stopping event server ...");
        stopEventServer();
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
        NodeWorkspace nodeWorkspace = nodeWorkspaceMap.get(nodeName);

        for (Map.Entry<String, String> entry: nodeService.getEnvironmentVariables().entrySet()) {
            environment.put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, String> entry: node.getEnvironmentVariables().entrySet()) {
            environment.put(entry.getKey(), entry.getValue());
        }

        environment.put(getNodeAppHomeEnvVar(nodeName), nodeWorkspace.getRootDirectory());

        return environment;
    }

    private String getNodeAppHomeEnvVar(String nodeName) {
        Node node = deployment.getNode(nodeName);
        Service nodeService = deployment.getService(node.getServiceName());

        String envVar = deployment.getAppHomeEnvVar();

        if (nodeService.getAppHomeEnvVar() != null) {
            envVar = nodeService.getAppHomeEnvVar();
        }

        if (node.getAppHomeEnvVar() != null) {
            envVar = node.getAppHomeEnvVar();
        }

        return envVar;
    }

    protected Map<String, String> getNodeEnvironmentVariablesMap(String nodeName) {
        Map<String, String> retMap = new HashMap<>();
        getNodeEnvironmentVariablesMap(nodeName, retMap);
        return retMap;
    }

    protected Set<String> getNodeLogFiles(Node node) {
        Set<String> logFiles = new HashSet<>(deployment.getService(node.getServiceName()).getLogFiles());
        logFiles.addAll(node.getLogFiles());
        return logFiles;
    }

    protected String getNodeLogFolder(Node node) {
        if (node.getLogFolder() != null) {
            return node.getLogFolder();
        }
        return deployment.getService(node.getServiceName()).getLogFolder();
    }

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
    protected abstract void stopNodes();
    public abstract void stopNode(String nodeName, boolean kill) throws RuntimeEngineException;
    public abstract void startNode(String nodeName) throws RuntimeEngineException;
    public abstract void restartNode(String nodeName) throws RuntimeEngineException;
    public abstract void clockDrift(String nodeName) throws RuntimeEngineException;
    public abstract void networkPartition(String nodeNames) throws RuntimeEngineException;

    public void setNodeWorkspaceMap(Map<String, NodeWorkspace> nodeWorkspaceMap) {
        this.nodeWorkspaceMap = nodeWorkspaceMap;
    }
}
