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

// TODO make this class cross-platform
public class WorkspaceManager {
    private final static Logger logger = LoggerFactory.getLogger(WorkspaceManager.class);

    private final Deployment deployment;
    private final Path workingDirectory;

    public WorkspaceManager(Deployment deployment) {
        this(deployment, Constants.DEFAULT_WORKING_DIRECTORY_NAME);
    }

    public WorkspaceManager(Deployment deployment, String workingDirectory) {
        this.deployment = deployment;
        this.workingDirectory = Paths.get(".", workingDirectory).toAbsolutePath().normalize();
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public Map<String, NodeWorkspace> createWorkspace(Deployment deployment) throws WorkspaceException {
        Map<String, NodeWorkspace> retMap = new HashMap<>();

        // Creates the working directory
        try {
            cleanUp();
            Files.createDirectory(workingDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating SpiderSilk working directory!");
        }

        // Creates the shared directories
        Map<String, String> sharedDirectoriesMap = createSharedDirectories();

        // Creates the nodes' workspaces
        for (Node node: deployment.getNodes().values()) {
            logger.info("Creating workspace for node {}", node.getName());
            retMap.put(node.getName(), createNodeWorkspace(node, sharedDirectoriesMap));
        }

        return Collections.unmodifiableMap(retMap);
    }


    public void cleanUp() throws WorkspaceException {
        logger.info("Cleaning up the working directory at {}", workingDirectory.toString());
        try {
            FileUtils.deleteDirectory(workingDirectory.toFile());
        } catch (IOException e) {
            throw new WorkspaceException("Error in cleaning up SpiderSilk working directory!");
        }
    }

    /**
     * This method replaces slashes in a path without slashes to be used as file or directory name
     * @param path to replace slashes in
     * @return the given path with replaced slashes
     */
    private String pathToStringWithoutSlashes(String path) {
        return path.replace("/", "_-_");
    }

    private Map<String, String> createSharedDirectories() throws WorkspaceException {
        Map<String, String> sharedDirectoriesMap = new HashMap<>();

        // Creating the shared directories
        Path sharedDirectoriesRoot = workingDirectory.resolve(Constants.SHAERD_DIRECTORIES_ROOT_NAME);
        try {
            Files.createDirectory(sharedDirectoriesRoot);
        } catch (IOException e) {
            logger.error("Error in creating shared directories root directory", e);
            throw new WorkspaceException("Error in creating shared directories root directory");
        }

        for (String path: deployment.getSharedDirectories()) {

            // Creating the shared directories
            try {
                Path sharedDirectory = sharedDirectoriesRoot.resolve(pathToStringWithoutSlashes(path));
                Files.createDirectory(sharedDirectory);
                sharedDirectoriesMap.put(sharedDirectory.toString(), path);
            } catch (IOException e) {
                logger.error("Error in creating shared directory {}", path, e);
                throw new WorkspaceException("Error in creating shared directory " + path);
            }
        }

        return sharedDirectoriesMap;
    }

    private NodeWorkspace createNodeWorkspace(Node node, Map<String, String> sharedDirectoriesMap) throws WorkspaceException {
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

        // Creates the node's log files
        Map<String, String> logFilesMap = createLogFiles(node, nodeRootDirectory, nodeLogDirectory);

        // Creates the node's log directories
        Map<String, String> logDirectoriesMap = createLogDirectories(node, nodeRootDirectory, nodeLogDirectory);

        Service nodeService = deployment.getService(node.getServiceName());

        // Copies over the node paths to the node root directory
        copyOverNodePaths(node, nodeService, nodeRootDirectory);

        String instrumentableAddress = nodeService.getInstrumentableAddress();
        if (instrumentableAddress != null) {
            instrumentableAddress = nodeRootDirectory.resolve(instrumentableAddress).toString();
        }

        return new NodeWorkspace(
                instrumentableAddress,
                getNodeLibPaths(nodeService, nodeRootDirectory),
                nodeWorkingDirectory.toString(),
                nodeRootDirectory.toString(),
                nodeLogDirectory.toString(),
                logDirectoriesMap, logFilesMap, sharedDirectoriesMap);
    }

    private Map<String, String> createLogFiles(Node node, Path nodeRootDirectory, Path nodeLogDirectory) throws WorkspaceException {
        Map<String, String> logFilesMap = new HashMap<>();

        for (String path: getNodeLogFiles(node, nodeRootDirectory)) {
            Path logFile = nodeLogDirectory.resolve(pathToStringWithoutSlashes(path));
            try {
                logFile.toFile().createNewFile();
                logFilesMap.put(logFile.toString(), FilenameUtils.normalize(path));
            } catch (IOException e) {
                logger.error("Error while creating log file {}", logFile, e);
                throw new WorkspaceException("Error while creating log file " + logFile);
            }
        }

        return logFilesMap;
    }

    private Map<String, String> createLogDirectories(Node node, Path nodeRootDirectory, Path nodeLogDirectory) throws WorkspaceException {
        Map<String, String> logDirectoriesMap = new HashMap<>();

        for (String path: getNodeLogDirectories(node, nodeRootDirectory)) {
            Path logDirectory = nodeLogDirectory.resolve(pathToStringWithoutSlashes(path));
            try {
                Files.createDirectory(logDirectory);
                logDirectoriesMap.put(logDirectory.toString(), FilenameUtils.normalize(path));
            } catch (IOException e) {
                logger.error("Error while creating log directory {}", logDirectory, e);
                throw new WorkspaceException("Error while creating log directory " + logDirectory);
            }
        }

        return logDirectoriesMap;
    }

    protected Set<String> getNodeLogFiles(Node node, Path nodeRootDirectory) {
        Set<String> logFiles = new HashSet<>(deployment.getService(node.getServiceName()).getLogFiles());
        logFiles.addAll(node.getLogFiles());
        logFiles = logFiles.stream().map(logFile -> improveNodeAddress(logFile, nodeRootDirectory)).collect(Collectors.toSet());
        return logFiles;
    }

    protected Set<String> getNodeLogDirectories(Node node, Path nodeRootDirectory) {
        Set<String> logDirectories = new HashSet<>(deployment.getService(node.getServiceName()).getLogDirectories());
        logDirectories.addAll(node.getLogDirectories());
        logDirectories = logDirectories.stream().map(logDirectory -> improveNodeAddress(logDirectory, nodeRootDirectory)).collect(Collectors.toSet());
        return logDirectories;
    }

    protected String improveNodeAddress(String address, Path nodeRootDirectory) {
        return address.replace("{{APP_HOME}}", nodeRootDirectory.toString());
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
            libPaths.add(nodeRootDirectory.resolve(libPath).normalize().toString());
        }

        for (String libPath: libPaths) {
            libPathsBuilder.append(libPath).append(nodeService.getServiceType().getLibraryPathSeparator());
        }

        return libPathsBuilder.toString();
    }

    private String pathEntryToLibPath(PathEntry pathEntry, Path nodeRootDirectory) {
        if (!pathEntry.shouldCopyOverToWorkspace()) {
            return pathEntry.getTargetPath();
        } else {
            return nodeRootDirectory.resolve(pathEntry.getTargetPath()).normalize().toString();
        }
    }

    private void copyOverNodePaths(Node node, Service nodeService, Path nodeRootDirectory) throws WorkspaceException {
        try {
            // Copies over node's service paths based on their entry path order
            for (PathEntry pathEntry : nodeService.getApplicationPaths().values().stream()
                    .sorted((p1, p2) -> p1.getOrder().compareTo(p2.getOrder()))
                    .collect(Collectors.toList())) {
                if (pathEntry.shouldCopyOverToWorkspace()) {
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
                if (pathEntry.shouldCopyOverToWorkspace()) {
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
            logger.error("Error in copying over node {} binaries to its workspace!", node.getName(), e);
            throw new WorkspaceException("Error in copying over node " + node.getName() + " binaries to its workspace!");
        }
    }
}
