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

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LoggingBuildHandler;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import me.arminb.spidersilk.Constants;
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

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public abstract class RuntimeEngine implements LimitedRuntimeEngine {
    private final static Logger logger = LoggerFactory.getLogger(RuntimeEngine.class);
    private final EventServer eventServer;
    protected final Deployment deployment;
    protected Map<String, NodeWorkspace> nodeWorkspaceMap;
    protected boolean stopped;
    protected DockerClient dockerClient;

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
        try {
            dockerClient = DefaultDockerClient.fromEnv().build();
        } catch (DockerCertificateException e) {
            throw new RuntimeEngineException("Cannot create docker client!");
        }

        // Configure local SpiderSilk runtime
        try {
            SpiderSilk.configure(HostUtil.getLocalIpAddress(), deployment.getEventServerPortNumber().toString());
        } catch (UnknownHostException e) {
            throw new RuntimeEngineException("Cannot get the local IP address to configure the local SpiderSilk runtime!");
        }

        // Exits if nodes' workspaces is not set
        if (nodeWorkspaceMap == null || nodeWorkspaceMap.isEmpty()) {
            throw new RuntimeEngineException("NodeWorkspaces is not set!");
        }

        // Builds docker images for the services if necessary
        logger.info("Building docker images ...");
        buildDockerImages();

        logger.info("Starting event server ...");
        startEventServer();
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

    private void buildDockerImages() throws RuntimeEngineException {
        for (Service service: deployment.getServices().values()) {
            try {
                if (service.getDockerImageForceBuild() ||
                        dockerClient.listImages(DockerClient.ListImagesParam.byName(service.getDockerImage())).isEmpty()) {
                    logger.info("Building docker image `{}` for service {} ...", service.getDockerImage(), service.getName());
                    Path dockerFile = Paths.get(service.getDockerFileAddress()).toAbsolutePath().normalize();
                    dockerClient.build(dockerFile.getParent(), service.getDockerImage(),
                            new LoggingBuildHandler(),
                            DockerClient.BuildParam.forceRm(),
                            DockerClient.BuildParam.dockerfile(dockerFile.getFileName()));
                }
            } catch (DockerException e) {
                throw new RuntimeEngineException("Error while building docker image for service " + service.getName() + "!");
            } catch (InterruptedException e) {
                throw new RuntimeEngineException("Error while building docker image for service " + service.getName() + "!");
            } catch (IOException e) {
                throw new RuntimeEngineException("Error while building docker image for service " + service.getName() + "!");
            }
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

    protected void startEventServer() throws RuntimeEngineException {
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
            environment.put(entry.getKey(), improveNodeAddress(nodeName, entry.getValue()));
        }

        for (Map.Entry<String, String> entry: node.getEnvironmentVariables().entrySet()) {
            environment.put(entry.getKey(), improveNodeAddress(nodeName, entry.getValue()));
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
        logFiles.stream().forEach(logFile -> improveNodeAddress(node.getName(), logFile));
        return logFiles;
    }

    protected String getNodeLogFolder(Node node) {
        Service nodeService = deployment.getService(node.getServiceName());
        if (node.getLogFolder() != null) {
            return improveNodeAddress(node.getName(), node.getLogFolder());
        }
        if (nodeService.getLogFolder() != null) {
            return improveNodeAddress(node.getName(), nodeService.getLogFolder());
        }
        return null;
    }

    protected String improveNodeAddress(String nodeName, String address) {
        return address.replace("{{APP_HOME}}", nodeWorkspaceMap.get(nodeName).getRootDirectory());
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

    public void setNodeWorkspaceMap(Map<String, NodeWorkspace> nodeWorkspaceMap) {
        this.nodeWorkspaceMap = nodeWorkspaceMap;
    }
}
