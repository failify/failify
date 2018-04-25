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

import com.google.common.collect.ImmutableList;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.EndpointConfig;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.NetworkConfig;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.entities.Service;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
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
    private final static String DOCKER_NETWORK_NAME_PREFIX = "spidersilk_";
    private final static String DOCKER_CONTAINER_NAME_PREFIX = "spidersilk_";
    private final static Integer SECONDS_TO_WAIT_BEFORE_KILLING_CONTAINER = 5;

    private Map<String, Process> processMap;
    private DockerClient dockerClient;
    private Map<String, String> nodeToContainerIdMap;
    private String dockerNetworkId;
    private String dockerNetworkName;

    public SingleNodeRuntimeEngine(Deployment deployment) {
        super(deployment);
        processMap = new HashMap<>();
        nodeToContainerIdMap = new HashMap<>();
        dockerNetworkId = null;
    }

    @Override
    protected void startNodes() throws RuntimeEngineException {
        try {
            dockerClient = DefaultDockerClient.fromEnv().build();
        } catch (DockerCertificateException e) {
            throw new RuntimeEngineException("Cannot create docker client!");
        }

        // creates a new docker network. This will be a new one every time the runtime engine starts.
        createDockerNetwork();

        for (Node node: deployment.getNodes().values()) {
            if (node.getOffOnStartup()) {
                logger.info("Skipping node " + node.getName() + " on startup since it is off!");
                continue;
            }

            logger.info("Starting node {} ...", node.getName());
            createAndStartNodeContainer(node);
        }
    }

    private void createDockerNetwork() throws RuntimeEngineException {
        try {
            dockerNetworkName = DOCKER_NETWORK_NAME_PREFIX + Instant.now().getEpochSecond();
            dockerNetworkId = dockerClient.createNetwork(NetworkConfig.builder()
                    .name(dockerNetworkName)
                    .build()).id();
            logger.info("Docker network {} is created!", dockerNetworkId);
        } catch (DockerException e) {
            throw new RuntimeEngineException("Error in creating docker network!");
        } catch (InterruptedException e) {
            throw new RuntimeEngineException("Error in creating docker network!");
        }
    }

    private void deleteDockerNetwork() throws RuntimeEngineException {
        try {
            if (dockerNetworkId != null) {
                dockerClient.removeNetwork(dockerNetworkId);
            }
        } catch (DockerException e) {
            throw new RuntimeEngineException("Error in deleting docker network" + dockerNetworkId + "!");
        } catch (InterruptedException e) {
            throw new RuntimeEngineException("Error in deleting docker network" + dockerNetworkId + "!");
        }
    }

    private void createAndStartNodeContainer(Node node) throws RuntimeEngineException {
        // TODO Add Tini init to avoid zombie processes
        Service nodeService = deployment.getService(node.getServiceName());
        NodeWorkspace nodeWorkspace = nodeWorkspaceMap.get(node.getName());

        ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder();
        HostConfig.Builder hostConfigBuilder = HostConfig.builder();
        // Sets the docker image for the container
        containerConfigBuilder.image(nodeService.getDockerImage());
        // Sets env vars for the container
        for (Map.Entry<String, String> envEntry: getNodeEnvironmentVariablesMap(node.getName()).entrySet()) {
            containerConfigBuilder.env(envEntry.getKey() + "=" + envEntry.getValue());
        }
        // Adds wrapper script to the node's root directory
        String wrapperScriptAddress = createWrapperScriptForNode(node);
        // Adds net admin capability to containers for iptables uses and make them connect to the created network
        hostConfigBuilder.capAdd("NET_ADMIN").networkMode(dockerNetworkName);
        // Adds workspace to the container and sets working directory
        hostConfigBuilder.appendBinds(HostConfig.Bind.from(nodeWorkspace.getRootDirectory())
                .to(nodeWorkspace.getRootDirectory()).readOnly(false).build());
        containerConfigBuilder.workingDir(nodeWorkspace.getRootDirectory());
        // Sets the network alias and hostname
        containerConfigBuilder.hostname(node.getName());
        Map<String, EndpointConfig> endpointConfigMap = new HashMap<>();
        endpointConfigMap.put(dockerNetworkName, EndpointConfig.builder()
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

        // Pulls the node's corresponding docker image
        logger.info("Pulling image `{}` for node {} ...", nodeService.getDockerImage(), node.getName());
        try {
            dockerClient.pull(nodeService.getDockerImage());
        } catch (DockerException e) {
            throw new RuntimeEngineException("Error while pulling docker image for node " + node.getName() + "!");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Creates and starts the container
        String containerName = DOCKER_CONTAINER_NAME_PREFIX + node.getName() + "_" + Instant.now().getEpochSecond();
        try {
            nodeToContainerIdMap.put(node.getName(),
                    dockerClient.createContainer(containerConfigBuilder.build(), containerName).id());
            logger.info("Container {} for node {} is created!", nodeToContainerIdMap.get(node.getName()), node.getName());
        } catch (DockerException e) {
            throw new RuntimeEngineException("Error while trying to create the container for node " + node.getName() + "!");
        } catch (InterruptedException e) {
            throw new RuntimeEngineException("Error while trying to create the container for node " + node.getName() + "!");
        }
        try {
            dockerClient.startContainer(nodeToContainerIdMap.get(node.getName()));
            logger.info("Container {} for node {} is started!", nodeToContainerIdMap.get(node.getName()), node.getName());
        } catch (DockerException e) {
            logger.error("Error while trying to start docker container for node {}", node.getName(), e);
            throw new RuntimeEngineException("Error while trying to start the container for node " + node.getName() + "!");
        } catch (InterruptedException e) {
            throw new RuntimeEngineException("Error while trying to start the container for node " + node.getName() + "!");
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
        for (Map.Entry<String, String> containerEntry: nodeToContainerIdMap.entrySet()) {
            try {
                // TODO the containers can also be deleted from the host
                dockerClient.stopContainer(containerEntry.getValue(), SECONDS_TO_WAIT_BEFORE_KILLING_CONTAINER);
                logger.info("Node {} container is stopped!", containerEntry.getKey());
            } catch (DockerException e) {
                logger.error("Error while stopping node {} container!", containerEntry.getKey(), e);
            } catch (InterruptedException e) {
                logger.error("Error while stopping node {} container!", containerEntry.getKey(), e);
            }
        }
        // deletes the created docker network
        try {
            logger.info("Deleting docker network {} ...", dockerNetworkId);
            deleteDockerNetwork();
            logger.info("Docker network is deleted successfully!");
        } catch (RuntimeEngineException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void stopNode(String nodeName, boolean kill) throws RuntimeEngineException {

        if (nodeToContainerIdMap.containsKey(nodeName)) {
            try {
                if (kill) {
                    dockerClient.killContainer(nodeToContainerIdMap.get(nodeName));
                } else {
                    dockerClient.stopContainer(nodeToContainerIdMap.get(nodeName), SECONDS_TO_WAIT_BEFORE_KILLING_CONTAINER);
                }
            } catch (InterruptedException e) {
                logger.error("An error occured while trying to stop the container for node {}!", nodeName);
            } catch (DockerException e) {
                logger.error("An error occured while trying to stop the container for node {}!", nodeName);
            }
        } else {
            logger.error("There is no container for node {} to be stopped!", nodeName);
        }
    }

    @Override
    public void startNode(String nodeName) throws RuntimeEngineException {

    }

    @Override
    public void restartNode(String nodeName) throws RuntimeEngineException {

    }

    @Override
    public void clockDrift(String nodeName) throws RuntimeEngineException {

    }

    @Override
    public void networkPartition(String nodeNames) throws RuntimeEngineException {

    }

}
