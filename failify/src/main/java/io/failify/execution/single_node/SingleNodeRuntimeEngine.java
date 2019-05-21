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

package io.failify.execution.single_node;

import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.ContainerNotFoundException;
import com.spotify.docker.client.exceptions.DockerRequestException;
import com.spotify.docker.client.shaded.com.google.common.collect.ImmutableList;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LoggingBuildHandler;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;
import com.spotify.docker.client.shaded.com.google.common.collect.ImmutableMap;
import io.failify.Constants;
import io.failify.dsl.entities.*;
import io.failify.exceptions.NodeIsNotRunningException;
import io.failify.exceptions.NodeNotFoundException;
import io.failify.execution.CommandResults;
import io.failify.execution.RuntimeEngine;
import io.failify.execution.ULimit;
import io.failify.util.DockerUtil;
import io.failify.util.HostUtil;
import io.failify.util.StreamUtil;
import io.failify.workspace.NodeWorkspace;
import io.failify.exceptions.RuntimeEngineException;
import io.failify.util.OsUtil;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class SingleNodeRuntimeEngine extends RuntimeEngine {
    private static Logger logger = LoggerFactory.getLogger(SingleNodeRuntimeEngine.class);

    private Map<String, DockerContainerInfo> nodeToContainerInfoMap;
    private DockerNetworkManager dockerNetworkManager;
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
                return dockerNetworkManager.getHostIpAddress();
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


    // TODO this method can be split up into fine-grained methods to be implemented by runtime engine children
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
        dockerNetworkManager = new DockerNetworkManager(deployment.getName(), dockerClient);

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
                dockerClient.connectToNetwork(dockerNetworkManager.dockerNetworkId(), NetworkConnection.builder()
                        .containerId(clientContainerId)
                        .endpointConfig(EndpointConfig.builder()
                                .ipAddress(dockerNetworkManager.getNewIpAddress())
                                .build())
                        .build());
            } catch (DockerException | InterruptedException e) {
                throw new RuntimeEngineException("Error while trying to add client container to the docker network "
                        + dockerNetworkManager.dockerNetworkId(), e);
            }
        }

        logger.info("Creating a container for each of the nodes ...");
        for (Node node: nodeMap.values()) {
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
    }

    public CommandResults runCommandInNode(String nodeName, String command) throws RuntimeEngineException {
        if (!nodeToContainerInfoMap.containsKey(nodeName)) {
            throw new NodeNotFoundException(nodeName);
        }

        ExecCreation execCreation;
        LogStream logStream;
        try {
            execCreation = dockerClient.execCreate(nodeToContainerInfoMap.get(nodeName).containerId(),
                    new String[] { "sh", "-c", command }, DockerClient.ExecCreateParam.attachStdin(),
                    DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr());
            logStream = dockerClient.execStart(execCreation.id());
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

        PipedOutputStream out_pipe, err_pipe;
        PipedInputStream out, err;
        try {
            out = new PipedInputStream();
            err = new PipedInputStream();
            out_pipe = new PipedOutputStream(out);
            err_pipe = new PipedOutputStream(err);

            AtomicReference<IOException> threadException = new AtomicReference<>();
            Thread readThread = new Thread(() -> {
                try {
                    logStream.attach(out_pipe, err_pipe);
                } catch (IOException e) {
                    threadException.set(e);
                }
            });

            readThread.start();
            try {
                readThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeEngineException("The join for read thread of command " + command + " has been interrupted!");
            }

            if (threadException.get() != null) {
                throw threadException.get();
            }

            String stdout = StreamUtil.pipedInputStreamToString(out);
            String stderr = StreamUtil.pipedInputStreamToString(err);
            return new CommandResults(nodeName, command, execState.exitCode(), stdout, stderr);
        } catch (IOException e) {
            throw new RuntimeEngineException("Error while reading the stdout and stderr for command " + command
                    + " on node " + nodeName);
        }
    }

    private void buildDockerImages() throws RuntimeEngineException {
        for (Service service: deployment.getServices().values()) {
            try {
                if (service.getDockerFileAddress() != null && (service.getDockerImageForceBuild() ||
                        dockerClient.listImages(DockerClient.ListImagesParam.byName(service.getDockerImageName())).isEmpty())) {
                    logger.info("Building docker image `{}` for service {} ...", service.getDockerImageName(), service.getName());
                    Path dockerFile = Paths.get(service.getDockerFileAddress());
                    dockerClient.build(dockerFile.getParent(), service.getDockerImageName(),
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

        if (isClockDriftEnabledInNode(nodeName)) {
            // Adds preload for libfaketime
            environment.put("LD_PRELOAD", Constants.FAKETIME_TARGET_BASE_PATH + Constants.FAKETIMEMT_LIB_FILE_NAME);
            // Disables offset caching for libfaketime
            environment.put("FAKETIME_NO_CACHE", "1");
            // Adds additional libfaketime config for java
            if (deployment.getService(nodeMap.get(nodeName).getServiceName()).getServiceType().isJvmType()) {
                environment.put("DONT_FAKE_MONOTONIC", "1");
            }
            // Adds controller file config for libfaketime
            environment.put("FAKETIME_TIMESTAMP_FILE", "/" + Constants.FAKETIME_CONTROLLER_FILE_NAME);
        }

        return environment;
    }

    @Override protected String getEventServerIpAddress() throws RuntimeEngineException {
        if (DockerUtil.isRunningInsideDocker()) {
            return dockerNetworkManager.getClientContainerIpAddress();
        } else {
            try {
                return HostUtil.getLocalIpAddress();
            } catch (UnknownHostException e) {
                throw new RuntimeEngineException("Unable to detect the local IP address of the client machine", e);
            }
        }
    }

    private String getDockerApiVersion() throws RuntimeEngineException {
        try {
            return dockerClient.version().apiVersion();
        } catch (DockerException | InterruptedException e) {
            throw new RuntimeEngineException("Error while getting docker version");
        }
    }

    private String bindMountString(String from, String to, boolean readonly) throws RuntimeEngineException {
        String apiVersion = getDockerApiVersion();
        String extra = Integer.parseInt(apiVersion.split("\\.")[1]) >= 28 ? ":delegated" : "";
        return OsUtil.getOS() == OsUtil.OS.LINUX ? from + ":" + to : from + ":" + to + extra;
    }

    // This should only work for linux containers
    @Override
    protected void createNodeContainer(Node node) throws RuntimeEngineException {
        // TODO Add Tini init to avoid zombie processes
        Service nodeService = deployment.getService(node.getServiceName());
        NodeWorkspace nodeWorkspace = nodeWorkspaceMap.get(node.getName());
        String newIpAddress = dockerNetworkManager.getNewIpAddress();

        String clientContainerId;
        try {
            clientContainerId = DockerUtil.getMyContainerId();
        } catch (IOException e) {
            throw new RuntimeEngineException("Cannot determine client's container id", e);
        }

        ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder();
        HostConfig.Builder hostConfigBuilder = HostConfig.builder();
        // Sets the docker image for the container
        containerConfigBuilder.image(nodeService.getDockerImageName());
        // Sets env vars for the container
        List<String> envList = new ArrayList<>();
        for (Map.Entry<String, String> envEntry: getNodeEnvironmentVariablesMap(node.getName()).entrySet()) {
            envList.add(envEntry.getKey() + "=" + envEntry.getValue());
        }
        containerConfigBuilder.env(envList);
        // Sets the working directory
        if (getNodeWorkDir(node.getName()) != null) {
            containerConfigBuilder.workingDir(getNodeWorkDir(node.getName()));
        }
        // Adds net admin capability to containers for iptables uses and make them connect to the created network
        hostConfigBuilder.capAdd("NET_ADMIN").networkMode(dockerNetworkManager.dockerNetworkName());
        // Creates do init file in the workspace and adds a bind mount for it
        try {
            Files.write(Paths.get(nodeWorkspace.getWorkingDirectory(), Constants.DO_INIT_FILE_NAME), "1".getBytes());
        } catch (IOException e) {
            throw new RuntimeEngineException("Error while creating failify do init file in node " + node.getName() + " workspace!", e);
        }
        hostConfigBuilder.appendBinds(bindMountString(DockerUtil.mapDockerPathToHostPath(dockerClient, clientContainerId,
                        Paths.get(nodeWorkspace.getWorkingDirectory(), Constants.DO_INIT_FILE_NAME).toAbsolutePath().toString())
                ,"/" + Constants.DO_INIT_FILE_NAME, false));
        // Adds all of the path mappings to the container
        for (NodeWorkspace.PathMappingEntry pathMappingEntry: nodeWorkspace.getPathMappingList()) {
            // TODO The readonly should come from path mapping. Right now docker wouldn't work with sub-path that are not readonly
            hostConfigBuilder.appendBinds(bindMountString(DockerUtil.mapDockerPathToHostPath(dockerClient,
                    clientContainerId, pathMappingEntry.getSource()), pathMappingEntry.getDestination(), false));
        }
        // Sets the network alias and hostname
        containerConfigBuilder.hostname(node.getName());
        Map<String, EndpointConfig> endpointConfigMap = new HashMap<>();
        endpointConfigMap.put(dockerNetworkManager.dockerNetworkName(), EndpointConfig.builder()
                .ipAddress(newIpAddress) // static ip address for containers
                .ipamConfig(EndpointConfig.EndpointIpamConfig.builder().ipv4Address(newIpAddress).build())
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
        hostConfigBuilder.appendBinds(bindMountString(DockerUtil.mapDockerPathToHostPath(dockerClient,
                clientContainerId, localConsoleFile), "/" + Constants.CONSOLE_OUTERR_FILE_NAME, false));
        // Adds bind mounts for shared directories
        for (String localSharedDirectory: nodeWorkspace.getSharedDirectoriesMap().keySet()) {
            hostConfigBuilder.appendBinds(bindMountString(DockerUtil.mapDockerPathToHostPath(dockerClient,
                    clientContainerId, localSharedDirectory), nodeWorkspace.getSharedDirectoriesMap()
                    .get(localSharedDirectory), false));
        }
        // Adds bind mounts for log directories
        for (String localLogDirectory: nodeWorkspace.getLogDirectoriesMap().keySet()) {
            hostConfigBuilder.appendBinds(bindMountString(DockerUtil.mapDockerPathToHostPath(dockerClient,
                    clientContainerId, localLogDirectory), nodeWorkspace.getLogDirectoriesMap()
                    .get(localLogDirectory), false));
        }
        // Adds bind mounts for log files
        for (String localLogFile: nodeWorkspace.getLogFilesMap().keySet()) {
            hostConfigBuilder.appendBinds(bindMountString(DockerUtil.mapDockerPathToHostPath(dockerClient,
                    clientContainerId, localLogFile), nodeWorkspace.getLogFilesMap()
                    .get(localLogFile), false));
        }
        // Adds bind mount for libfaketime controller file
        // TODO file creation should be moved to WorkspaceManager
        String localLibFakeTimeFile = getLocalLibFakeTimeControllerFile(node.getName());
        try {
            new File(localLibFakeTimeFile).createNewFile();
        } catch (IOException e) {
            throw new RuntimeEngineException("Error while creating libfaketime controller file for node " + node.getName() + "!", e);
        }
        hostConfigBuilder.appendBinds(bindMountString(DockerUtil.mapDockerPathToHostPath(dockerClient,
                clientContainerId, localLibFakeTimeFile), "/" + Constants.FAKETIME_CONTROLLER_FILE_NAME, false));

        // only use wrapper script if startcommand or initcommand are present
        if (getNodeStartCommand(node.getName()) != null || getNodeInitCommand(node.getName()) != null) {
            // Creates the wrapper script and adds a bind mount for it
            String wrapperFile = createWrapperScriptForNode(node);
            String wrapperScriptAddress = DockerUtil.mapDockerPathToHostPath(dockerClient, clientContainerId, wrapperFile);
            hostConfigBuilder.appendBinds(bindMountString(wrapperScriptAddress, "/" + Constants.WRAPPER_SCRIPT_NAME, true));
            // Sets the wrapper script as the starting command
            containerConfigBuilder.cmd("/bin/sh", "-c", "/" + Constants.WRAPPER_SCRIPT_NAME + " >> /" +
                    Constants.CONSOLE_OUTERR_FILE_NAME + " 2>&1");
        }
        // Sets ulimits for the container
        List<HostConfig.Ulimit> ulimits = new ArrayList<>();
        for (ULimit ulimit: nodeService.ulimits().keySet()) {
            ULimit.Values values = nodeService.ulimits().get(ulimit);
            ulimits.add(HostConfig.Ulimit.builder().name(ulimit.name().toLowerCase())
                    .soft(values.soft()).hard(values.hard()).build());
        }
        hostConfigBuilder.ulimits(ulimits);
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

        if (dockerNetworkManager != null) {
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
                        dockerClient.disconnectFromNetwork(clinetContainerId, dockerNetworkManager.dockerNetworkId());
                    } catch (DockerException | InterruptedException e) {
                        logger.error("Error while trying to remove client container from the docker network " + dockerNetworkManager
                                .dockerNetworkId(), e);
                    }
                }
            }

            // deletes the created docker network
            try {
                logger.info("Deleting docker network {} ...", dockerNetworkManager.dockerNetworkId());
                dockerNetworkManager.deleteDockerNetwork();
                logger.info("Docker network is deleted successfully!");
            } catch (RuntimeEngineException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private void removeContainer(String nodeName) throws RuntimeEngineException {
        logger.info("Removing container for node {} ...", nodeName);
        try {
            dockerClient.removeContainer(nodeToContainerInfoMap.get(nodeName).containerId(),
                    DockerClient.RemoveContainerParam.forceKill());
            logger.info("Node {} container is removed!", nodeName);
        } catch (InterruptedException | DockerException e) {
            throw new RuntimeEngineException("Error while trying to remove the container for node " + nodeName + "!", e);
        }
    }

    @Override
    public void killNode(String nodeName) throws RuntimeEngineException {
        if (nodeToContainerInfoMap.containsKey(nodeName)) {
            logger.info("Killing node {} ...", nodeName);
            try {
                dockerClient.killContainer(nodeToContainerInfoMap.get(nodeName).containerId());
                logger.info("Node {} is killed!", nodeName);
            } catch (DockerRequestException e) {
                // TODO maybe find a better way to do this
                if (!(e.status() == 500 && e.getResponseBody().toLowerCase().contains("not running"))) {
                    throw new RuntimeEngineException("Error while trying to kill the container for node " + nodeName + "!", e);
                }
            } catch (InterruptedException | DockerException e) {
                throw new RuntimeEngineException("Error while trying to kill the container for node " + nodeName + "!", e);
            }
        } else {
            throw new NodeNotFoundException(nodeName);
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
                    try {
                        runCommandInNode(nodeName, stopCommand);
                    } catch (NodeIsNotRunningException e) {
                        logger.debug("Stop command for node {} cant be executed since the node is not running", nodeName);
                    }
                }
                dockerClient.stopContainer(nodeToContainerInfoMap.get(nodeName).containerId(), secondsUntilForcedStop);
                logger.info("Node {} is stopped!", nodeName);
            } catch (NodeIsNotRunningException | ContainerNotFoundException e) {
                logger.debug("Node {} is not running. Node stop is not needed.");
            } catch (InterruptedException | DockerException e) {
                throw new RuntimeEngineException("Error while trying to stop the container for node " + nodeName + "!", e);
            }
        } else {
            throw new NodeNotFoundException(nodeName);
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
                networkOperationManager.reApplyNetworkOperations(nodeName);
                networkPartitionManager.reApplyNetworkPartition(nodeName);
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
                Files.write(Paths.get(nodeWorkspaceMap.get(nodeName).getWorkingDirectory(), "failify_do_init"),
                        "0".getBytes());
            } catch (IOException e) {
                throw new RuntimeEngineException("Error while changing failify do init file in node " + nodeName + " workspace!", e);
            }

            updateContainerPortMapping(nodeName);

            logger.info("Node {} is started!", nodeName);
        } else {
            throw new NodeNotFoundException(nodeName);
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
                    try {
                        runCommandInNode(nodeName, stopCommand);
                    } catch (NodeIsNotRunningException e) {
                        logger.debug("Stop command for node {} cant be executed since the node is not running", nodeName);
                    }
                }
                dockerClient.restartContainer(nodeToContainerInfoMap.get(nodeName).containerId());
                networkOperationManager.reApplyNetworkOperations(nodeName);
                networkPartitionManager.reApplyNetworkPartition(nodeName);
                updateContainerPortMapping(nodeName);
                logger.info("Node {} is restarted!", nodeName);
            } catch (InterruptedException | DockerException e) {
                throw new RuntimeEngineException("Error while trying to restart the container for node " + nodeName + "!", e);
            }
        } else {
            throw new NodeNotFoundException(nodeName);
        }
    }

    @Override
    public synchronized void clockDrift(String nodeName, Integer amount) throws RuntimeEngineException {
        if (!nodeToContainerInfoMap.containsKey(nodeName)) {
            throw new NodeNotFoundException(nodeName);
        }

        if (!isClockDriftEnabledInNode(nodeName)) {
            logger.warn("Clock drift is not enabled in node {}. Operation ignored!", nodeName);
            return;
        }

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
    protected void startFileSharingService() {
        // File sharing comes for free with docker. No additional service is needed.
    }

    @Override
    protected void stopFileSharingService() {
        // File sharing comes for free with docker. No additional service is needed.
    }
}
