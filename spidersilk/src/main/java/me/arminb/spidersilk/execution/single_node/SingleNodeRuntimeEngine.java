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

package me.arminb.spidersilk.execution.single_node;

import com.spotify.docker.client.exceptions.ContainerNotFoundException;
import com.spotify.docker.client.shaded.com.google.common.collect.ImmutableList;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LoggingBuildHandler;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;
import com.spotify.docker.client.shaded.com.google.common.collect.ImmutableMap;
import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.dsl.entities.*;
import me.arminb.spidersilk.exceptions.NodeIsNotRunningException;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.execution.RuntimeEngine;
import me.arminb.spidersilk.util.DockerUtil;
import me.arminb.spidersilk.util.HostUtil;
import me.arminb.spidersilk.util.OsUtil;
import me.arminb.spidersilk.workspace.NodeWorkspace;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class SingleNodeRuntimeEngine extends RuntimeEngine {
    private static Logger logger = LoggerFactory.getLogger(SingleNodeRuntimeEngine.class);

    private Map<String, DockerContainerInfo> nodeToContainerInfoMap;
    private DockerNetworkManager networkManager;
    private DockerClient dockerClient;

    public SingleNodeRuntimeEngine(Deployment deployment, Map<String, NodeWorkspace> nodeWorkspaceMap) {
        super(deployment, nodeWorkspaceMap);
        nodeToContainerInfoMap = new HashMap<>();
    }

    public String ip(String nodeName) {
        if (!nodeToContainerInfoMap.containsKey(nodeName)) {
            return null;
        }

        if (DockerUtil.isRunningInsideDocker()) {
            // This is possible because the client container is added to the created docker network
            return nodeToContainerInfoMap.get(nodeName).ip();
        } else {
            if (OsUtil.getOS() == OsUtil.OS.LINUX) {
                return nodeToContainerInfoMap.get(nodeName).ip();
            } else {
                return "127.0.0.1";
            }
        }
    }

    @Override
    public Integer portMapping(String nodeName, Integer portNumber, PortType portType) {
        if (!nodeToContainerInfoMap.containsKey(nodeName)) {
            return null;
        }

        if (DockerUtil.isRunningInsideDocker()) {
            // This is possible because the client container is added to the created docker network
            return portNumber;
        } else {
            if (OsUtil.getOS() == OsUtil.OS.LINUX) {
                return portNumber;
            } else {
                return nodeToContainerInfoMap.get(nodeName)
                        .getPortMapping(new ExposedPortDefinition(portNumber, portType));
            }
        }
    }

    public String getNodeContainerId(String nodeName) {
        return nodeToContainerInfoMap.get(nodeName).containerId();
    }

    @Override
    protected void startNodes() throws RuntimeEngineException {
        // Creates the docker client
        try {
            dockerClient = DefaultDockerClient.fromEnv().build();
        } catch (DockerCertificateException e) {
            throw new RuntimeEngineException("Cannot create docker client!", e);
        }

        // Builds docker images for the services if necessary
        logger.info("Building docker images ...");
        buildDockerImages();

        // creates a new docker network. This will be a new one every time the runtime engine starts.
        networkManager = new DockerNetworkManager(deployment.getName(), this, dockerClient);

        // If the client is a docker container, adds the container to the created docker network
        if (DockerUtil.isRunningInsideDocker()) {
            logger.info("Adding client container to the created docker network ...");
            String clientContainerId;
            try {
                 clientContainerId = DockerUtil.getMyContainerId();
            } catch (IOException e) {
                throw new RuntimeEngineException("Cannot determine client's container id", e);
            }
            logger.info("Client container id is {}", clientContainerId);
            try {
                dockerClient.connectToNetwork(networkManager.dockerNetworkId(), NetworkConnection.builder()
                        .containerId(clientContainerId)
                        .endpointConfig(EndpointConfig.builder()
                                .ipAddress(networkManager.getNewIpAddress())
                                .build())
                        .build());
            } catch (DockerException | InterruptedException e) {
                throw new RuntimeEngineException("Error while trying to add client container to the docker network "
                        + networkManager.dockerNetworkId(), e);
            }
        }

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

        for (String nodeName: nodeToContainerInfoMap.keySet()) {
            logger.info("Node {} ip address is: {}", nodeName, nodeToContainerInfoMap.get(nodeName).ip());
        }

        // We don't want any other change in this map later
        nodeToContainerInfoMap = Collections.unmodifiableMap(nodeToContainerInfoMap);
    }

    public long runCommandInNode(String nodeName, String command) throws RuntimeEngineException {
        if (!nodeToContainerInfoMap.containsKey(nodeName)) {
            // TODO should this throw exception
            logger.error("Node {} does not have a running container to run command {}!", nodeName, command);
            return -1;
        }

        ExecCreation execCreation;

        try {
            execCreation = dockerClient.execCreate(nodeToContainerInfoMap.get(nodeName).containerId(),
                    command.split("\\s+"));
            dockerClient.execStart(execCreation.id());
        } catch (InterruptedException e) {
            throw new RuntimeEngineException("Error while trying to run command " + command + " in node " + nodeName + "!", e);
        } catch (ContainerNotFoundException | IllegalStateException e) {
            throw new NodeIsNotRunningException("No running container for node " + nodeName + " has been found to execute command "
                    + command, e);
        } catch (DockerException e) {
            throw new RuntimeEngineException("Error while trying to run command " + command + " in node "
                    + nodeName + "!", e);
        }

        ExecState execState;

        try {
            do {
                Thread.sleep(10);
                execState = dockerClient.execInspect(execCreation.id());
            } while (execState.running());
        } catch (InterruptedException e) {
            throw new RuntimeEngineException("Error while trying to inspect the status of command " + command
                    + " in node " + nodeName + "!", e);
        } catch (ContainerNotFoundException | IllegalStateException e) {
            throw new NodeIsNotRunningException("No running container for node " + nodeName + " has been found to execute command"
                    + command, e);
        } catch (DockerException e) {
            throw new RuntimeEngineException("Error while trying to inspect the status of command " + command
                    + " in node " + nodeName + "!", e);
        }

        return execState.exitCode();
    }

    private void buildDockerImages() throws RuntimeEngineException {
        for (Service service: deployment.getServices().values()) {
            try {
                if (service.getDockerImageForceBuild() ||
                        dockerClient.listImages(DockerClient.ListImagesParam.byName(service.getDockerImage())).isEmpty()) {
                    logger.info("Building docker image `{}` for service {} ...", service.getDockerImage(), service.getName());
                    Path dockerFile = Paths.get(service.getDockerFileAddress());
                    dockerClient.build(dockerFile.getParent(), service.getDockerImage(),
                            new LoggingBuildHandler(),
                            DockerClient.BuildParam.forceRm(),
                            DockerClient.BuildParam.dockerfile(dockerFile.getFileName()));
                }
            } catch (InterruptedException | IOException | DockerException e) {
                throw new RuntimeEngineException("Error while building docker image for service " + service.getName() + "!", e);
            }
        }
    }

    @Override
    protected Map<String, String> improveEnvironmentVariablesMapForEngine(String nodeName, Map<String, String> environment)
            throws RuntimeEngineException {

        // Adds preload for libfaketime
        environment.put("LD_PRELOAD", Constants.FAKETIME_TARGET_BASE_PATH + Constants.FAKETIMEMT_LIB_FILE_NAME);
        // Disables offset caching for libfaketime
        environment.put("FAKETIME_NO_CACHE", "1");
        // Adds additional libfaketime config for java
        if (deployment.getService(deployment.getNode(nodeName).getServiceName()).getServiceType() == ServiceType.JAVA) {
            environment.put("DONT_FAKE_MONOTONIC", "1");
        }
        // Adds controller file config for libfaketime
        environment.put("FAKETIME_TIMESTAMP_FILE", "/" + Constants.FAKETIME_CONTROLLER_FILE_NAME);

        return environment;
    }

    @Override protected String getEventServerIpAddress() throws RuntimeEngineException {
        if (DockerUtil.isRunningInsideDocker()) {
            return networkManager.getClientContainerIpAddress();
        } else {
            try {
                return HostUtil.getLocalIpAddress();
            } catch (UnknownHostException e) {
                throw new RuntimeEngineException("Unable to detect the local IP address of the client machine", e);
            }
        }
    }

    // This should only work for linux containers
    private void createNodeContainer(Node node) throws RuntimeEngineException {
        // TODO Add Tini init to avoid zombie processes
        Service nodeService = deployment.getService(node.getServiceName());
        NodeWorkspace nodeWorkspace = nodeWorkspaceMap.get(node.getName());
        String newIpAddress = networkManager.getNewIpAddress();

        String clientContainerId;
        try {
            clientContainerId = DockerUtil.getMyContainerId();
        } catch (IOException e) {
            throw new RuntimeEngineException("Cannot determine client's container id", e);
        }

        ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder();
        HostConfig.Builder hostConfigBuilder = HostConfig.builder();
        // Sets the docker image for the container
        containerConfigBuilder.image(nodeService.getDockerImage());
        // Sets env vars for the container
        List<String> envList = new ArrayList<>();
        for (Map.Entry<String, String> envEntry: getNodeEnvironmentVariablesMap(node.getName()).entrySet()) {
            envList.add(envEntry.getKey() + "=" + envEntry.getValue());
        }
        containerConfigBuilder.env(envList);
        // Creates the wrapper script and adds a bind mount for it
        String wrapperFile = createWrapperScriptForNode(node);
        String wrapperScriptAddress = DockerUtil.mapDockerPathToHostPath(dockerClient, clientContainerId,
                wrapperFile);
        hostConfigBuilder.appendBinds(HostConfig.Bind.from(wrapperScriptAddress)
                .to("/" + Constants.WRAPPER_SCRIPT_NAME).readOnly(true).build());
        // Adds net admin capability to containers for iptables uses and make them connect to the created network
        hostConfigBuilder.capAdd("NET_ADMIN").networkMode(networkManager.dockerNetworkName());
        // Creates do init file in the workspace and adds a bind mount for it
        try {
            Files.write(Paths.get(nodeWorkspace.getWorkingDirectory(), Constants.DO_INIT_FILE_NAME), "1".getBytes());
        } catch (IOException e) {
            throw new RuntimeEngineException("Error while spidersilk do init file in node " + node.getName() + " workspace!", e);
        }
        hostConfigBuilder.appendBinds(HostConfig.Bind
                .from(DockerUtil.mapDockerPathToHostPath(dockerClient, clientContainerId,
                        Paths.get(nodeWorkspace.getWorkingDirectory(), Constants.DO_INIT_FILE_NAME).toAbsolutePath().toString()))
                .to("/" + Constants.DO_INIT_FILE_NAME).readOnly(false).build());
        // Adds all of the path mappings to the container
        for (NodeWorkspace.PathMappingEntry pathMappingEntry: nodeWorkspace.getPathMappingList()) {
            // TODO The readonly should come from path mapping. Right now docker wouldn't work with sub-path that are not readonly
            hostConfigBuilder.appendBinds(HostConfig.Bind.from(DockerUtil.mapDockerPathToHostPath(dockerClient,
                    clientContainerId, pathMappingEntry.getSource()))
                    .to(pathMappingEntry.getDestination()).readOnly(false).build());
        }
        // Sets the network alias and hostname
        containerConfigBuilder.hostname(node.getName());
        Map<String, EndpointConfig> endpointConfigMap = new HashMap<>();
        endpointConfigMap.put(networkManager.dockerNetworkName(), EndpointConfig.builder()
                .ipAddress(newIpAddress) // static ip address for containers
                .aliases(ImmutableList.<String>builder().add(node.getName()).build()).build());
        containerConfigBuilder.networkingConfig(ContainerConfig.NetworkingConfig.create(endpointConfigMap));
        // Sets exposed ports
        if (!DockerUtil.isRunningInsideDocker() && OsUtil.getOS() != OsUtil.OS.LINUX) {
            containerConfigBuilder.exposedPorts(
                    getNodeExposedPorts(node.getName()).stream().map(portDef -> portDef.toString()).collect(Collectors.toSet()));
            hostConfigBuilder.publishAllPorts(true);
        }
        // Adds bind mount for console output
        // TODO file creation should be moved to WorkspaceManager
        String localConsoleFile = Paths.get(nodeWorkspace.getLogDirectory(), Constants.CONSOLE_OUTERR_FILE_NAME).toAbsolutePath().toString();
        try {
            new File(localConsoleFile).createNewFile();
        } catch (IOException e) {
            throw new RuntimeEngineException("Error while creating initial console log file for node " + node.getName() + "!", e);
        }
        hostConfigBuilder.appendBinds(HostConfig.Bind.from(DockerUtil.mapDockerPathToHostPath(dockerClient,
                clientContainerId, localConsoleFile)).to("/" + Constants.CONSOLE_OUTERR_FILE_NAME).build());
        // Adds bind mounts for shared directories
        for (String localSharedDirectory: nodeWorkspace.getSharedDirectoriesMap().keySet()) {
            hostConfigBuilder.appendBinds(HostConfig.Bind.from(DockerUtil.mapDockerPathToHostPath(dockerClient,
                    clientContainerId, localSharedDirectory)).to(nodeWorkspace.getSharedDirectoriesMap()
                    .get(localSharedDirectory)).readOnly(false).build());
        }
        // Adds bind mounts for log directories
        for (String localLogDirectory: nodeWorkspace.getLogDirectoriesMap().keySet()) {
            hostConfigBuilder.appendBinds(HostConfig.Bind.from(DockerUtil.mapDockerPathToHostPath(dockerClient,
                    clientContainerId, localLogDirectory)).to(nodeWorkspace.getLogDirectoriesMap()
                    .get(localLogDirectory)).readOnly(false).build());
        }
        // Adds bind mounts for log files
        for (String localLogFile: nodeWorkspace.getLogFilesMap().keySet()) {
            hostConfigBuilder.appendBinds(HostConfig.Bind.from(DockerUtil.mapDockerPathToHostPath(dockerClient,
                    clientContainerId, localLogFile)).to(nodeWorkspace.getLogFilesMap()
                    .get(localLogFile)).readOnly(false).build());
        }
        // Adds bind mount for libfaketime controller file
        // TODO file creation should be moved to WorkspaceManager
        String localLibFakeTimeFile = getLocalLibFakeTimeControllerFile(node.getName());
        try {
            new File(localLibFakeTimeFile).createNewFile();
        } catch (IOException e) {
            throw new RuntimeEngineException("Error while creating libfaketime controller file for node " + node.getName() + "!", e);
        }
        hostConfigBuilder.appendBinds(HostConfig.Bind.from(DockerUtil.mapDockerPathToHostPath(dockerClient,
                clientContainerId, localLibFakeTimeFile)).to("/" + Constants.FAKETIME_CONTROLLER_FILE_NAME).build());

        // Sets the wrapper script as the starting command
        containerConfigBuilder.cmd("/bin/sh", "-c", "/" + Constants.WRAPPER_SCRIPT_NAME + " >> /" +
                Constants.CONSOLE_OUTERR_FILE_NAME + " 2>&1");
        // Finalizing host config
        containerConfigBuilder.hostConfig(hostConfigBuilder.build());
        // Creates the container
        String containerName = Constants.DOCKER_CONTAINER_NAME_PREFIX + deployment.getName() + "_" + node.getName() + "_" + Instant.now().getEpochSecond();
        try {
            nodeToContainerInfoMap.put(node.getName(), new DockerContainerInfo(
                    dockerClient.createContainer(containerConfigBuilder.build(), containerName).id(), newIpAddress));
            logger.info("Container {} for node {} is created!", nodeToContainerInfoMap.get(node.getName()).containerId(), node.getName());
        } catch (InterruptedException | DockerException e) {
            throw new RuntimeEngineException("Error while trying to create the container for node " + node.getName() + "!", e);
        }
    }

    private String getLocalLibFakeTimeControllerFile(String nodeName) {
        return Paths.get(nodeWorkspaceMap.get(nodeName).getWorkingDirectory(), Constants.FAKETIME_CONTROLLER_FILE_NAME)
                .toAbsolutePath().toString();
    }

    /**
     * This method creates a customized wrapper script for the node in its root directory
     * @return the address of wrapper script
     */
    private String createWrapperScriptForNode(Node node) throws RuntimeEngineException {
        File wrapperScriptFile = Paths.get(nodeWorkspaceMap.get(node.getName()).getRootDirectory())
                .resolve("wrapper_script").toFile();

        try {
            String wrapperScriptString = IOUtils.toString(ClassLoader.getSystemResourceAsStream("wrapper_script"),
                    StandardCharsets.UTF_8);
            String initCommand = getNodeInitCommand(node.getName());
            String startCommand = getNodeStartCommand(node.getName());

            if (initCommand != null) {
                wrapperScriptString = wrapperScriptString.replace("{{INIT_COMMAND}}", initCommand);
            } else {
                wrapperScriptString = wrapperScriptString.replace("{{INIT_COMMAND}}", ":");
            }
            wrapperScriptString = wrapperScriptString.replace("{{START_COMMAND}}", startCommand);

            FileOutputStream fileOutputStream = new FileOutputStream(wrapperScriptFile);
            IOUtils.write(wrapperScriptString, fileOutputStream, StandardCharsets.UTF_8);
            fileOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeEngineException("Error while creating wrapper script for node " + node.getName() + "!", e);
        }

        wrapperScriptFile.setExecutable(true);
        wrapperScriptFile.setReadable(true);
        wrapperScriptFile.setWritable(true);

        return wrapperScriptFile.toString();
    }

    @Override
    protected void stopNodes(Boolean kill, Integer secondsUntilForcedStop) {
        // stops all of the running containers
        logger.info("Stopping containers ...");
        for (String nodeName: nodeToContainerInfoMap.keySet()) {
            try {
                if (kill) {
                    killNode(nodeName);
                } else {
                    stopNode(nodeName, secondsUntilForcedStop);
                }
            } catch (RuntimeEngineException e) {
                logger.warn("Error while trying to stop the container for node {}!", nodeName);
            }
            try {
                removeContainer(nodeName);
            } catch (RuntimeEngineException e) {
                logger.warn("Error while trying to remove the container for node {}!", nodeName);
            }
        }

        if (networkManager != null) {
            // If the client is a docker container, removes the container from the created docker network
            if (DockerUtil.isRunningInsideDocker()) {
                logger.info("Removing client container from the created docker network ...");
                String clinetContainerId = null;
                try {
                    clinetContainerId = DockerUtil.getMyContainerId();
                } catch (IOException e) {
                    logger.error("Cannot determine client's container id", e);
                }
                if (clinetContainerId != null && !clinetContainerId.isEmpty()) {
                    try {
                        dockerClient.disconnectFromNetwork(clinetContainerId, networkManager.dockerNetworkId());
                    } catch (DockerException | InterruptedException e) {
                        logger.error("Error while trying to remove client container from the docker network " + networkManager.dockerNetworkId(), e);
                    }
                }
            }

            // deletes the created docker network
            try {
                logger.info("Deleting docker network {} ...", networkManager.dockerNetworkId());
                networkManager.deleteDockerNetwork();
                logger.info("Docker network is deleted successfully!");
            } catch (RuntimeEngineException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public void removeContainer(String nodeName) throws RuntimeEngineException {
        if (nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.info("Removing container for node {} ...", nodeName);
            try {
                dockerClient.removeContainer(nodeToContainerInfoMap.get(nodeName).containerId(),
                        DockerClient.RemoveContainerParam.forceKill());
                logger.info("Node {} container is removed!", nodeName);
            } catch (InterruptedException | DockerException e) {
                throw new RuntimeEngineException("Error while trying to remove the container for node " + nodeName + "!", e);
            }
        } else {
            // TODO should this throw exception
            logger.error("There is no container for node {} to be removed!", nodeName);
        }
    }

    @Override
    public void killNode(String nodeName) throws RuntimeEngineException {
        if (nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.info("Killing node {} ...", nodeName);
            try {
                dockerClient.killContainer(nodeToContainerInfoMap.get(nodeName).containerId());
                logger.info("Node {} container is killed!", nodeName);
            } catch (InterruptedException | DockerException e) {
                throw new RuntimeEngineException("Error while trying to kill the container for node " + nodeName + "!", e);
            }
        } else {
            // TODO should this throw exception
            logger.error("There is no container for node {} to be killed!", nodeName);
        }
    }

    @Override
    public void stopNode(String nodeName, Integer secondsUntilForcedStop) throws RuntimeEngineException {
        if (nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.info("Stopping node {} ...", nodeName);
            try {
                // Runs stop command. useful for stopping daemon processes gracefully
                String stopCommand = getNodeStopCommand(nodeName);
                if (stopCommand != null) {
                    runCommandInNode(nodeName, stopCommand);
                }
                dockerClient.stopContainer(nodeToContainerInfoMap.get(nodeName).containerId(), secondsUntilForcedStop);
                logger.info("Node {} container is stopped!", nodeName);
            } catch (InterruptedException | DockerException e) {
                throw new RuntimeEngineException("Error while trying to stop the container for node " + nodeName + "!", e);
            }
        } else {
            // TODO should this throw exception
            logger.error("There is no container for node {} to be stopped!", nodeName);
        }
    }

    private void updateContainerPortMapping(String nodeName) throws RuntimeEngineException {
        ContainerInfo containerInfo;
        try {
            containerInfo = dockerClient.inspectContainer(nodeToContainerInfoMap.get(nodeName).containerId());
        } catch (InterruptedException | DockerException e) {
            throw new RuntimeEngineException("Error while trying to inspect the status of node " + nodeName + "!", e);
        }

        Map<ExposedPortDefinition, Integer> portMapping = new HashMap<>();
        ImmutableMap<String, List<PortBinding>> ports = containerInfo.networkSettings().ports();
        for (String containerPort: ports.keySet()) {
            portMapping.put(ExposedPortDefinition.fromString(containerPort),
                    Integer.parseInt(ports.get(containerPort).get(0).hostPort()));
        }
        nodeToContainerInfoMap.get(nodeName).setPortMapping(portMapping);
    }

    @Override
    public void startNode(String nodeName) throws RuntimeEngineException {
        if (nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.info("Starting node {} ...", nodeName);

            String containerId = nodeToContainerInfoMap.get(nodeName).containerId();

            try {
                dockerClient.startContainer(containerId);
                networkManager.reApplyIptablesRules(nodeName);
            } catch (InterruptedException | DockerException e) {
                throw new RuntimeEngineException("Error while trying to start the container for node " + nodeName + "!", e);
            }
            // Prevents the init command to be executed in the next run of this node
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeEngineException("Error while waiting for node " + nodeName + " to get started!", e);
            }
            try {
                Files.write(Paths.get(nodeWorkspaceMap.get(nodeName).getWorkingDirectory(), "spidersilk_do_init"),
                        "0".getBytes());
            } catch (IOException e) {
                throw new RuntimeEngineException("Error while spidersilk do init file in node " + nodeName + " workspace!", e);
            }

            updateContainerPortMapping(nodeName);

            logger.info("Container {} for node {} is started!", containerId, nodeName);
        } else {
            // TODO should this throw exception
            logger.error("There is no container for node {} to be started!", nodeName);
        }
    }

    @Override
    public void restartNode(String nodeName, Integer secondsUntilForcedStop) throws RuntimeEngineException {
        if (nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.info("Restarting node {} ...", nodeName);
            try {
                // Runs stop command. useful for restarting daemon processes gracefully
                String stopCommand = getNodeStopCommand(nodeName);
                if (stopCommand != null) {
                    runCommandInNode(nodeName, stopCommand);
                }
                dockerClient.restartContainer(nodeToContainerInfoMap.get(nodeName).containerId());
                networkManager.reApplyIptablesRules(nodeName);
                updateContainerPortMapping(nodeName);
                logger.info("Container {} for node {} is restarted!", nodeToContainerInfoMap.get(nodeName).containerId(), nodeName);
            } catch (InterruptedException | DockerException e) {
                throw new RuntimeEngineException("Error while trying to restart the container for node " + nodeName + "!", e);
            }
        } else {
            // TODO should this throw exception
            logger.error("There is no container for node {} to be restarted!", nodeName);
        }
    }

    @Override
    public void clockDrift(String nodeName, Integer amount) throws RuntimeEngineException {
        logger.info("Applying clock drift {},{}", nodeName, amount);
        Path localLibFakeTimeFile = Paths.get(getLocalLibFakeTimeControllerFile(nodeName));
        String stringAmount = amount.toString();

        // Adds the missing + sign if necessary
        if (!stringAmount.startsWith("-") && !stringAmount.startsWith("+")) {
            stringAmount = "+" + stringAmount;
        }

        // Adds comma as the fraction delimiter. If necessary will add 0 to the right
        // 1000 => +1,000 , 10 => +0,010 , -1 => -0,001
        if (stringAmount.length() > 4) {
            stringAmount = stringAmount.substring(0, stringAmount.length() - 3) + "," +
                    stringAmount.substring(stringAmount.length() - 3);
        } else {
            String stringNumber = stringAmount.substring(1);
            for (int i = 0; i < 4 - stringAmount.length(); i++) {
                stringNumber = "0" + stringNumber;
            }
            stringAmount = stringAmount.charAt(0) + "0," + stringNumber;
        }

        try {
            Files.write(localLibFakeTimeFile, (stringAmount + "\n").getBytes());
        } catch (IOException e) {
            throw new RuntimeEngineException("Error while writing into libfaketime controller file for node " + nodeName, e);
        }
    }

    @Override
    public void linkDown(String node1, String node2) throws RuntimeEngineException {
        networkManager.linkDown(node1, node2);
    }

    @Override
    public void linkUp(String node1, String node2) throws RuntimeEngineException {
        networkManager.linkUp(node1, node2);
    }

    @Override
    public void networkPartition(String nodePartitions) throws RuntimeEngineException {
        networkManager.networkPartition(nodePartitions);
    }

    @Override
    public void removeNetworkPartition() throws RuntimeEngineException {
        networkManager.removeNetworkPartition();
    }

    @Override
    protected void startFileSharingService() {
        // File sharing comes for free with docker. No additional service is needed.
    }

    @Override
    protected void stopFileSharingService() {
        // File sharing comes for free with docker. No additional service is needed.
    }
}
