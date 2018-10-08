package me.arminb.spidersilk.workspace;

import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.entities.PathEntry;
import me.arminb.spidersilk.dsl.entities.Service;
import me.arminb.spidersilk.exceptions.WorkspaceException;
import me.arminb.spidersilk.util.FileUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class WorkspaceManager {
    private final static Logger logger = LoggerFactory.getLogger(WorkspaceManager.class);

    private final Deployment deployment;
    private final Path workingDirectory;

    public WorkspaceManager(Deployment deployment) {
        this(deployment, Constants.DEFAULT_WORKING_DIRECTORY_NAME);
    }

    public WorkspaceManager(Deployment deployment, String workingDirectory) {
        this.deployment = deployment;
        this.workingDirectory = Paths.get(FilenameUtils.normalize(Paths.get(".", workingDirectory)
                .toAbsolutePath().toString()));
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public Map<String, NodeWorkspace> createWorkspace(Deployment deployment) {
        Map<String, NodeWorkspace> retMap = new HashMap<>();

        // Creates the working directory
        try {
            cleanUp();
            Files.createDirectory(workingDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating SpiderSilk working directory!");
        }

        // Creates the nodes' workspaces
        for (Node node: deployment.getNodes().values()) {
            logger.info("Creating workspce for node {}", node.getName());
            retMap.put(node.getName(), createNodeWorkspace(node));
        }

        return Collections.unmodifiableMap(retMap);
    }

    public void cleanUp() {
        logger.info("Cleaning up the working directory at {}", workingDirectory.toAbsolutePath().toString());
        try {
            FileUtils.deleteDirectory(workingDirectory.toFile());
        } catch (IOException e) {
            throw new WorkspaceException("Error in cleaning up SpiderSilk working directory!");
        }
    }

    private NodeWorkspace createNodeWorkspace(Node node) {
        // Creates the node's working directory
        Path nodeWorkingDirectory = workingDirectory.resolve(node.getName());
        try {
            Files.createDirectory(nodeWorkingDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating SpiderSilk node working directory \""
                    + node.getName() + "\"!");
        }

        // Creates the node root directory
        Path nodeRootDirectory = nodeWorkingDirectory.resolve(Constants.NODE_ROOT_DIRECTORY_NAME);
        try {
            Files.createDirectory(nodeRootDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating SpiderSilk node root directory \""
                    + node.getName() + "\"!");
        }

        // Creates the node's log directory
        Path nodeLogDirectory = nodeWorkingDirectory.resolve(Constants.NODE_LOG_DIRECTORY_NAME);
        try {
            Files.createDirectory(nodeLogDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating SpiderSilk node log directory \""
                    + node.getName() + "\"!");
        }

        Service nodeService = deployment.getService(node.getServiceName());

        // copies over the node paths to the node root directory
        copyOverNodePaths(node, nodeService, nodeRootDirectory);

        String instrumentableAddress = nodeService.getInstrumentableAddress();
        if (instrumentableAddress != null) {
            instrumentableAddress = FilenameUtils.normalize(nodeRootDirectory.resolve(instrumentableAddress).toAbsolutePath().toString());
        }

        return new NodeWorkspace(
                instrumentableAddress,
                getNodeLibPaths(nodeService, nodeRootDirectory),
                nodeWorkingDirectory.toAbsolutePath().toString(),
                nodeRootDirectory.toAbsolutePath().toString(),
                nodeLogDirectory.toAbsolutePath().toString()
        );
    }

    private String getNodeLibPaths(Service nodeService, Path nodeRootDirectory) {
        StringBuilder libPathsBuilder = new StringBuilder();
        Set<String> libPaths = new HashSet<>();

        // Adds application paths that are library to the set
        for (PathEntry pathEntry: nodeService.getApplicationPaths().values()) {
            if (pathEntry.isLibrary()) {
                libPaths.add(pathEntryToLibPath(pathEntry, nodeRootDirectory));
            }
        }

        // Adds marked relative library paths
        for (String libPath: nodeService.getLibraryPaths()) {
            libPaths.add(FilenameUtils.normalize(nodeRootDirectory.resolve(libPath).toAbsolutePath().toString()));
        }

        for (String libPath: libPaths) {
            libPathsBuilder.append(libPath).append(nodeService.getServiceType().getLibraryPathSeparator());
        }

        return libPathsBuilder.toString();
    }

    private String pathEntryToLibPath(PathEntry pathEntry, Path nodeRootDirectory) {
        if (pathEntry.isShared()) {
            return pathEntry.getTargetPath();
        } else {
            return FilenameUtils.normalize(
                    nodeRootDirectory.resolve(pathEntry.getTargetPath()).toAbsolutePath().toString());
        }
    }

    private void copyOverNodePaths(Node node, Service nodeService, Path nodeRootDirectory) {
        try {
            // Copies over node's service paths based on their entry path order
            for (PathEntry pathEntry : nodeService.getApplicationPaths().values().stream()
                    .sorted((p1, p2) -> p1.getOrder().compareTo(p2.getOrder()))
                    .collect(Collectors.toList())) {
                if (!pathEntry.isShared()) {
                    if (new File(pathEntry.getPath()).isDirectory()) {
                        FileUtil.copyDirectory(Paths.get(pathEntry.getPath()),
                                nodeRootDirectory.resolve(pathEntry.getTargetPath()));
                    } else {
                        nodeRootDirectory.resolve(pathEntry.getTargetPath()).toFile().mkdirs();
                        Files.copy(Paths.get(pathEntry.getPath()),
                                nodeRootDirectory.resolve(pathEntry.getTargetPath()), StandardCopyOption.COPY_ATTRIBUTES,
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // Copies over node's paths based on their entry path order
            for (PathEntry pathEntry : node.getApplicationPaths().values().stream()
                    .sorted((p1, p2) -> p1.getOrder().compareTo(p2.getOrder()))
                    .collect(Collectors.toList())) {
                if (!pathEntry.isShared()) {
                    if (new File(pathEntry.getPath()).isDirectory()) {
                        FileUtil.copyDirectory(Paths.get(pathEntry.getPath()),
                                nodeRootDirectory.resolve(pathEntry.getTargetPath()));
                    } else {
                        nodeRootDirectory.resolve(pathEntry.getTargetPath()).toFile().mkdirs();
                        Files.copy(Paths.get(pathEntry.getPath()),
                                nodeRootDirectory.resolve(pathEntry.getTargetPath()), StandardCopyOption.COPY_ATTRIBUTES,
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            throw new WorkspaceException("Error in copying over node " + node.getName() + " binaries to its workspace!");
        }
    }
}
