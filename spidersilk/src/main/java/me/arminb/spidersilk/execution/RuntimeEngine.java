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

import me.arminb.spidersilk.SpiderSilkRunner;
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

public abstract class RuntimeEngine implements LimitedRuntimeEngine {
    private final static Logger logger = LoggerFactory.getLogger(RuntimeEngine.class);
    private final EventServer eventServer;
    protected final Deployment deployment;
    protected Map<String, NodeWorkspace> nodeWorkspaceMap;
    protected boolean stopped;

    public RuntimeEngine(Deployment deployment) {
        this.stopped = true;
        this.deployment = deployment;
        eventServer = new EventServer(deployment);
        EventService.initialize(deployment);
    }

    public Set<String> nodeNames() {
        return deployment.getNodes().keySet();
    }

    public boolean isStopped() {
        return stopped;
    }

    public void start(SpiderSilkRunner spiderSilkRunner) throws RuntimeEngineException {
        // Configure local SpiderSilk runtime
        try {
            if (eventServer.getPortNumber() == null) {
                throw new RuntimeEngineException("The event server's port number is not known since the event server is not started yet!");
            }
            SpiderSilk.configure(HostUtil.getLocalIpAddress(), String.valueOf(eventServer.getPortNumber()));
        } catch (UnknownHostException e) {
            throw new RuntimeEngineException("Cannot get the local IP address to configure the local SpiderSilk runtime!");
        }

        // Exits if nodes' workspaces is not set
        if (nodeWorkspaceMap == null || nodeWorkspaceMap.isEmpty()) {
            throw new RuntimeEngineException("NodeWorkspaces is not set!");
        }

        if (!deployment.getSharedDirectories().isEmpty()) {
            logger.info("Starting file sharing service ...");
            startFileSharingService();
        }
        logger.info("Starting external events ...");
        startExternalEvents(spiderSilkRunner);

        try {
            logger.info("Starting nodes ...");
            stopped = false;
            startNodes();
        } catch (RuntimeEngineException e) {
            stop();
            throw e;
        }
    }

    protected void startExternalEvents(SpiderSilkRunner spiderSilkRunner) {
        // Find those external events that are present in the run sequence
        List<ExternalEvent> externalEvents = new ArrayList<>();
        for (String id: deployment.getRunSequence().split("\\W+")) {
            if (deployment.getExternalEvent(id) != null) {
                externalEvents.add(deployment.getExternalEvent(id));
            }
        }

        for (ExternalEvent externalEvent: externalEvents) {
            externalEvent.start(spiderSilkRunner);
        }
    }

    public void startEventServer() throws RuntimeEngineException {
        eventServer.start();
    }

    public void stop() {
        logger.info("Stopping the runtime engine ...");
        logger.info("Stopping external events ...");
        stopExternalEvents();
        logger.info("Stopping nodes ...");
        stopNodes();
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

    protected Map<String, String> getNodeEnvironmentVariablesMap(String nodeName) {
        Map<String, String> retMap = new HashMap<>();
        getNodeEnvironmentVariablesMap(nodeName, retMap);
        return retMap;
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



    public void waitFor(String eventName) throws RuntimeEngineException {
        if (deployment.isInRunSequence(eventName)) {
            logger.info("Waiting for event {} in workload ...", eventName);
            SpiderSilk.getInstance().blockAndPoll(eventName, true);
        } else {
            throw new RuntimeEngineException("Event " + eventName + " is not referred to in the run sequence. Thus," +
                    " its order cannot be enforced!");
        }
    }

    @Override
    public void enforceOrder(String eventName) throws RuntimeEngineException {
        if (deployment.workloadEventExists(eventName) && deployment.isInRunSequence(eventName)) {
            logger.info("Enforcing order for workload event {} ...", eventName);
            SpiderSilk.getInstance().enforceOrder(eventName, null);
        } else {
            throw new RuntimeEngineException("Event " + eventName + " is not a defined workload event or is not referred to" +
                    " in the run sequence. Thus, its order cannot be enforced from the workload!");
        }
    }

    @Override
    public void sendEvent(String eventName)throws RuntimeEngineException {
        if (deployment.workloadEventExists(eventName)) {
            SpiderSilk.getInstance().sendEvent(eventName);
        } else {
            throw new RuntimeEngineException("Event " + eventName + " is not a defined workload event and" +
                    " cannot be sent from the workload!");
        }
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

    public void setNodeWorkspaceMap(Map<String, NodeWorkspace> nodeWorkspaceMap) {
        this.nodeWorkspaceMap = Collections.unmodifiableMap(nodeWorkspaceMap);
    }

    public Integer getEventServerPortNumber() {
        return eventServer.getPortNumber();
    }
}
