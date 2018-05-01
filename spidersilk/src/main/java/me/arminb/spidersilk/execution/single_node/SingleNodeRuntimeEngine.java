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

package me.arminb.spidersilk.execution.single_node;

import com.google.common.collect.ImmutableList;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;
import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.entities.Service;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.execution.RuntimeEngine;
import me.arminb.spidersilk.workspace.NodeWorkspace;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

public class SingleNodeRuntimeEngine extends RuntimeEngine {
    private static Logger logger = LoggerFactory.getLogger(SingleNodeRuntimeEngine.class);

    private Map<String, DockerContainerInfo> nodeToContainerInfoMap;
    private DockerNetworkManager networkManager;

    public SingleNodeRuntimeEngine(Deployment deployment) {
        super(deployment);
        nodeToContainerInfoMap = new HashMap<>();
    }

    public String ip(String nodeName) {
        return nodeToContainerInfoMap.get(nodeName).ip();
    }

    public String getNodeContainerId(String nodeName) {
        return nodeToContainerInfoMap.get(nodeName).containerId();
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    @Override
    protected void startNodes() throws RuntimeEngineException {
        // creates a new docker network. This will be a new one every time the runtime engine starts.
        networkManager = new DockerNetworkManager(this);

        logger.info("Creating a container for each of the nodes ...");
        for (Node node: deployment.getNodes().values()) {
            // Creates a container for the node
            createNodeContainer(node);
            // Starts the container if it is not off on startup
            if (node.getOffOnStartup()) {
                logger.info("Skipping node " + node.getName() + " on startup since it is off!");
            } else {
                startNode(node.getName());
            }
        }

        // We don't want any other change in this map later
        nodeToContainerInfoMap = Collections.unmodifiableMap(nodeToContainerInfoMap);
    }

    public int runCommandInNode(String nodeName, String command) throws DockerException, InterruptedException {
        if (!nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.error("Node {} does not have a running container to run command {}!", nodeName, command);
            return -1;
        }

        ExecCreation execCreation = dockerClient.execCreate(nodeToContainerInfoMap.get(nodeName).containerId(),
                command.split("\\s+"));
        dockerClient.execStart(execCreation.id());

        ExecState execState;
        do {
            Thread.sleep(10);
            execState = dockerClient.execInspect(execCreation.id());
        } while (execState.running());

        return execState.exitCode();
    }

    private void createNodeContainer(Node node) throws RuntimeEngineException {
        // TODO Add Tini init to avoid zombie processes
        // TODO is this cross-platform?
        Service nodeService = deployment.getService(node.getServiceName());
        NodeWorkspace nodeWorkspace = nodeWorkspaceMap.get(node.getName());
        String newIpAddress = networkManager.getNewIpAddress();

        ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder();
        HostConfig.Builder hostConfigBuilder = HostConfig.builder();
        // Sets the docker image for the container
        containerConfigBuilder.image(nodeService.getDockerImage());
        // Adds auto remove after stop
        hostConfigBuilder.autoRemove(true);
        // Sets env vars for the container
        for (Map.Entry<String, String> envEntry: getNodeEnvironmentVariablesMap(node.getName()).entrySet()) {
            containerConfigBuilder.env(envEntry.getKey() + "=" + envEntry.getValue());
        }
        // Adds wrapper script to the node's root directory
        String wrapperScriptAddress = createWrapperScriptForNode(node);
        // Adds net admin capability to containers for iptables uses and make them connect to the created network
        hostConfigBuilder.capAdd("NET_ADMIN").networkMode(networkManager.dockerNetworkName());
        // Adds workspace to the container and sets working directory
        hostConfigBuilder.appendBinds(HostConfig.Bind.from(nodeWorkspace.getRootDirectory())
                .to(nodeWorkspace.getRootDirectory()).readOnly(false).build());
        containerConfigBuilder.workingDir(nodeWorkspace.getRootDirectory());
        // Sets the network alias and hostname
        containerConfigBuilder.hostname(node.getName());
        Map<String, EndpointConfig> endpointConfigMap = new HashMap<>();
        endpointConfigMap.put(networkManager.dockerNetworkName(), EndpointConfig.builder()
                .ipAddress(newIpAddress) // static ip address for containers
                .aliases(ImmutableList.<String>builder().add(node.getName()).build()).build());
        containerConfigBuilder.networkingConfig(ContainerConfig.NetworkingConfig.create(endpointConfigMap));
        // Adds bind mount for console output
        String localConsoleFile = Paths.get(nodeWorkspace.getLogDirectory(), "spidersilk_out_err").toAbsolutePath().toString();
        try {
            new File(localConsoleFile).createNewFile();
        } catch (IOException e) {
            throw new RuntimeEngineException("Error while creating initial console log file for node " + node.getName() + "!");
        }
        hostConfigBuilder.appendBinds(HostConfig.Bind.from(localConsoleFile).to("/spidersilk_out_err").build());
        // Adds bind mount for log folder
        if (getNodeLogFolder(node) != null) {
            hostConfigBuilder.appendBinds(HostConfig.Bind.from(nodeWorkspace.getLogDirectory()).to(getNodeLogFolder(node)).build());
        }
        // Adds bind mounts for log files
        for (String logFile: getNodeLogFiles(node)) {
            String localLogFileName = Paths.get(nodeWorkspace.getLogDirectory(), new File(logFile).getName())
                    .toAbsolutePath().toString();
            try {
                new File(localLogFileName).createNewFile();
            } catch (IOException e) {
                throw new RuntimeEngineException("Error while creating initial log file " + logFile
                        + " for node " + node.getName() + "!");
            }
            hostConfigBuilder.appendBinds(HostConfig.Bind.from(localLogFileName).to(logFile).build());
        }
        // Sets the wrapper script as the starting command
        containerConfigBuilder.cmd(wrapperScriptAddress);
        // Finalizing host config
        containerConfigBuilder.hostConfig(hostConfigBuilder.build());
        // Creates the container
        String containerName = Constants.DOCKER_CONTAINER_NAME_PREFIX + node.getName() + "_" + Instant.now().getEpochSecond();
        try {
            nodeToContainerInfoMap.put(node.getName(), new DockerContainerInfo(
                    dockerClient.createContainer(containerConfigBuilder.build(), containerName).id(), newIpAddress));
            logger.info("Container {} for node {} is created!", nodeToContainerInfoMap.get(node.getName()), node.getName());
        } catch (DockerException e) {
            throw new RuntimeEngineException("Error while trying to create the container for node " + node.getName() + "!");
        } catch (InterruptedException e) {
            throw new RuntimeEngineException("Error while trying to create the container for node " + node.getName() + "!");
        }
    }

    /**
     * This method creates a customized wrapper script for the node in its root directory
     * @return the address of wrapper script
     */
    private String createWrapperScriptForNode(Node node) throws RuntimeEngineException {
        Service nodeService = deployment.getService(node.getServiceName());
        File wrapperScriptFile = new File(nodeWorkspaceMap.get(node.getName()).getRootDirectory() + "/wrapper_script");

        String runCommand = nodeService.getRunCommand();
        if (node.getRunCommand() != null) {
            runCommand = node.getRunCommand();
        }

        try {
            String wrapperScriptString = IOUtils.toString(ClassLoader.getSystemResourceAsStream("wrapper_script"),
                    StandardCharsets.UTF_8).replace("{{RUN_COMMAND}}", runCommand);
            FileOutputStream fileOutputStream = new FileOutputStream(wrapperScriptFile);
            IOUtils.write(wrapperScriptString, fileOutputStream, StandardCharsets.UTF_8);
            fileOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeEngineException("Error while creating wrapper script for node " + node.getName() + "!");
        }

        wrapperScriptFile.setExecutable(true);
        wrapperScriptFile.setReadable(true);
        wrapperScriptFile.setWritable(true);

        return wrapperScriptFile.getAbsolutePath();
    }

    @Override
    protected void stopNodes() {
        // stops all of the running containers
        logger.info("Stopping containers ...");
        for (String nodeName: nodeToContainerInfoMap.keySet()) {
            try {
                stopNode(nodeName, deployment.getSecondsUntilForcedStop());
            } catch (RuntimeEngineException e) {
                logger.error("Error while trying to stop the container for node {}!", nodeName);
            }
        }
        // deletes the created docker network
        try {
            logger.info("Deleting docker network {} ...", networkManager.dockerNetworkId());
            networkManager.deleteDockerNetwork();
            logger.info("Docker network is deleted successfully!");
        } catch (RuntimeEngineException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void killNode(String nodeName) throws RuntimeEngineException {
        if (nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.info("Killing node {} ...", nodeName);
            try {
                dockerClient.killContainer(nodeToContainerInfoMap.get(nodeName).containerId());
                logger.info("Node {} container is killed!", nodeName);
            } catch (InterruptedException e) {
                throw new RuntimeEngineException("Error while trying to kill the container for node " + nodeName + "!");
            } catch (DockerException e) {
                throw new RuntimeEngineException("Error while trying to kill the container for node " + nodeName + "!");
            }
        } else {
            logger.error("There is no container for node {} to be killed!", nodeName);
        }
    }

    @Override
    public void stopNode(String nodeName, Integer secondsUntilForcedStop) throws RuntimeEngineException {
        if (nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.info("Stopping node {} ...", nodeName);
            try {
                dockerClient.stopContainer(nodeToContainerInfoMap.get(nodeName).containerId(), secondsUntilForcedStop);
                logger.info("Node {} container is stopped!", nodeName);
            } catch (InterruptedException e) {
                throw new RuntimeEngineException("Error while trying to stop the container for node " + nodeName + "!");
            } catch (DockerException e) {
                throw new RuntimeEngineException("Error while trying to stop the container for node " + nodeName + "!");
            }
        } else {
            logger.error("There is no container for node {} to be stopped!", nodeName);
        }
    }

    @Override
    public void startNode(String nodeName) throws RuntimeEngineException {
        if (nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.info("Starting node {} ...", nodeName);
            try {
                dockerClient.startContainer(nodeToContainerInfoMap.get(nodeName).containerId());
                networkManager.reApplyIptablesRules(nodeName);
                logger.info("Container {} for node {} is started!", nodeToContainerInfoMap.get(nodeName).containerId(), nodeName);
            } catch (DockerException e) {
                throw new RuntimeEngineException("Error while trying to start the container for node " + nodeName + "!");
            } catch (InterruptedException e) {
                throw new RuntimeEngineException("Error while trying to start the container for node " + nodeName + "!");
            }
        } else {
            logger.error("There is no container for node {} to be started!", nodeName);
        }
    }

    @Override
    public void restartNode(String nodeName, Integer secondsUntilForcedStop) throws RuntimeEngineException {
        if (nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.info("Restarting node {} ...", nodeName);
            try {
                dockerClient.restartContainer(nodeToContainerInfoMap.get(nodeName).containerId());
                networkManager.reApplyIptablesRules(nodeName);
                logger.info("Container {} for node {} is restarted!", nodeToContainerInfoMap.get(nodeName).containerId(), nodeName);
            } catch (DockerException e) {
                throw new RuntimeEngineException("Error while trying to restart the container for node " + nodeName + "!");
            } catch (InterruptedException e) {
                throw new RuntimeEngineException("Error while trying to restart the container for node " + nodeName + "!");
            }
        } else {
            logger.error("There is no container for node {} to be restarted!", nodeName);
        }
    }

    @Override
    public void clockDrift(String nodeName) throws RuntimeEngineException {

    }

    @Override
    public void networkPartition(String nodePartitions) throws RuntimeEngineException {
        networkManager.networkPartition(nodePartitions);
    }

    @Override
    public void removeNetworkPartition() throws RuntimeEngineException {
        networkManager.removeNetworkPartition();
    }
}
