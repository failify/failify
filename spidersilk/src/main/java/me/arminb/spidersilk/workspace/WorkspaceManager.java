package me.arminb.spidersilk.workspace;

import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.entities.PathEntry;
import me.arminb.spidersilk.dsl.entities.Service;
import me.arminb.spidersilk.exceptions.OsNotSupportedException;
import me.arminb.spidersilk.exceptions.WorkspaceException;
import me.arminb.spidersilk.util.FileUtil;
import me.arminb.spidersilk.util.ZipUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class WorkspaceManager {
    private final static Logger logger = LoggerFactory.getLogger(WorkspaceManager.class);

    private final Deployment deployment;
    private final Path workingDirectory;

    public WorkspaceManager(Deployment deployment) {
        this(deployment, Constants.DEFAULT_WORKING_DIRECTORY_NAME);
    }

    public WorkspaceManager(Deployment deployment, String topLevelWorkingDirectory) {
        this.deployment = deployment;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MMM-dd_HH-mm-ss-SSS");
        this.workingDirectory = Paths.get(".", topLevelWorkingDirectory, deployment.getName() + "_" +
                simpleDateFormat.format(new Date())).toAbsolutePath().normalize();
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public Map<String, NodeWorkspace> createWorkspace(Deployment deployment) throws WorkspaceException {
        Map<String, NodeWorkspace> retMap = new HashMap<>();

        // Creates the working directory
        try {
            logger.info("Creating the working directory at {}", workingDirectory.toString());
            Files.createDirectories(workingDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating SpiderSilk working directory at " + workingDirectory.toString());
        }

        // Creates the shared directories
        Map<String, String> sharedDirectoriesMap = createSharedDirectories();

        // Decompress compressed application paths in services
        Map<String, Map<String, String>> serviceToMapOfCompressedToDecompressedMap = decompressCompressedApplicationPaths();

        // Creates the nodes' workspaces
        for (Node node: deployment.getNodes().values()) {
            logger.info("Creating workspace for node {}", node.getName());
            retMap.put(node.getName(), createNodeWorkspace(node, sharedDirectoriesMap,
                    serviceToMapOfCompressedToDecompressedMap.get(node.getServiceName())));
        }

        return Collections.unmodifiableMap(retMap);
    }

    /**
     * This method replaces slashes in a path without slashes to be used as file or directory name
     * @param path to replace slashes in
     * @return the given path with replaced slashes
     */
    private String pathToStringWithoutSlashes(String path) {
        return path.replaceAll("[\\\\/]", "_-");
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

    private Map<String, Map<String, String>> decompressCompressedApplicationPaths() throws WorkspaceException {
        Map<String, Map<String, String>> retMap = new HashMap<>();
        Path decompressedDirectory = workingDirectory.resolve(Constants.DECOMPRESSED_DIRECTORIES_ROOT_NAME);

        try {
            Files.createDirectory(decompressedDirectory);
        } catch (IOException e) {
            logger.error("Error in creating decompressed directory at {}", decompressedDirectory, e);
            throw new WorkspaceException("Error in creating decompressed directory at " + decompressedDirectory);
        }

        for (Service service: deployment.getServices().values()) {
            retMap.put(service.getName(), new HashMap<>());
            for (PathEntry pathEntry: service.getApplicationPaths().values()) {
                if (pathEntry.shouldBeDecompressed()) {
                    if (pathEntry.getPath().endsWith(".zip")) {
                        File targetDir = decompressedDirectory.resolve(service.getName())
                                .resolve(pathToStringWithoutSlashes(pathEntry.getPath())).toFile();

                        try {
                            ZipUtil.unzip(pathEntry.getPath(), targetDir.toString());
                        } catch (IOException e) {
                            logger.error("Error while unzipping {}", pathEntry.getPath(), e);
                            throw new WorkspaceException("Error while unzipping " + pathEntry.getPath());
                        } catch (InterruptedException e) {
                            logger.error("Error while unzipping {}", pathEntry.getPath(), e);
                            throw new WorkspaceException("Error while unzipping " + pathEntry.getPath());
                        } catch (OsNotSupportedException e) {
                            logger.error(e.getMessage(), pathEntry.getPath(), e);
                            throw new WorkspaceException(e.getMessage());
                        }
                        retMap.get(service.getName()).put(pathEntry.getPath(), targetDir.toString());
                    } else {
                        throw new WorkspaceException("Decompression is only supported for zip files!"
                                + pathEntry.getPath() + " is not a zip file.");
                    }
                }
            }
        }
        return retMap;
    }

    private NodeWorkspace createNodeWorkspace(Node node, Map<String, String> sharedDirectoriesMap,
                                              Map<String, String> compressedToDecompressedMap) throws WorkspaceException {
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
        Map<String, String> logFilesMap = createLogFiles(node, nodeLogDirectory);

        // Creates the node's log directories
        Map<String, String> logDirectoriesMap = createLogDirectories(node, nodeLogDirectory);

        Service nodeService = deployment.getService(node.getServiceName());

        // Copies over the node paths to the node root directory
        List<NodeWorkspace.PathMappingEntry> pathMappingList = copyOverNodePathsAndMakePathMappingList(node, nodeService,
                nodeRootDirectory, compressedToDecompressedMap);

        // Determines the instrumentable paths
        Set<String> instrumentablePaths = new HashSet<>();
        for (String instrumentablePath: nodeService.getInstrumentablePaths()) {
            instrumentablePaths.add(getLocalPathFromNodeTargetPath(pathMappingList, instrumentablePath, true));
        }

        // Creates the node workspace object
        return new NodeWorkspace(
                instrumentablePaths,
                getNodeLibPaths(nodeService, pathMappingList),
                nodeWorkingDirectory.toString(),
                nodeRootDirectory.toString(),
                nodeLogDirectory.toString(),
                logDirectoriesMap, logFilesMap, sharedDirectoriesMap, pathMappingList);
    }

    private Map<String, String> createLogFiles(Node node, Path nodeLogDirectory) throws WorkspaceException {
        Map<String, String> logFilesMap = new HashMap<>();

        for (String path: getNodeLogFiles(node)) {
            Path logFile = nodeLogDirectory.resolve(pathToStringWithoutSlashes(path));
            try {
                logFile.toFile().createNewFile();
                logFilesMap.put(logFile.toString(), path);
            } catch (IOException e) {
                logger.error("Error while creating log file {}", logFile, e);
                throw new WorkspaceException("Error while creating log file " + logFile);
            }
        }

        return logFilesMap;
    }

    private Map<String, String> createLogDirectories(Node node, Path nodeLogDirectory) throws WorkspaceException {
        Map<String, String> logDirectoriesMap = new HashMap<>();

        for (String path: getNodeLogDirectories(node)) {
            Path logDirectory = nodeLogDirectory.resolve(pathToStringWithoutSlashes(path));
            try {
                Files.createDirectory(logDirectory);
                logDirectoriesMap.put(logDirectory.toString(), path);
            } catch (IOException e) {
                logger.error("Error while creating log directory {}", logDirectory, e);
                throw new WorkspaceException("Error while creating log directory " + logDirectory);
            }
        }

        return logDirectoriesMap;
    }

    protected Set<String> getNodeLogFiles(Node node) {
        Set<String> logFiles = new HashSet<>(deployment.getService(node.getServiceName()).getLogFiles());
        logFiles.addAll(node.getLogFiles());
        return logFiles;
    }

    protected Set<String> getNodeLogDirectories(Node node) {
        Set<String> logDirectories = new HashSet<>(deployment.getService(node.getServiceName()).getLogDirectories());
        logDirectories.addAll(node.getLogDirectories());
        return logDirectories;
    }

    private Set<String> getNodeLibPaths(Service nodeService, List<NodeWorkspace.PathMappingEntry> pathMappingList) throws WorkspaceException {
        Set<String> libPaths = new HashSet<>();

        // Adds application paths that are library to the set
        for (PathEntry pathEntry : nodeService.getApplicationPaths().values()) {
            if (pathEntry.isLibrary()) {
                String localLibPath = getLocalPathFromNodeTargetPath(pathMappingList, pathEntry.getTargetPath(),
                        false);
                if (localLibPath != null) {
                    try {
                        libPaths.addAll(FileUtil.findAllMatchingPaths(localLibPath));
                    } catch (IOException e) {
                        logger.error("Error while trying to expand lib path {}", pathEntry.getTargetPath(), e);
                        throw new WorkspaceException("Error while trying to expand lib path " + pathEntry.getTargetPath());
                    }
                }
            }
        }

        // Adds marked library paths
        for (String libPath : nodeService.getLibraryPaths()) {
            String localLibPath = getLocalPathFromNodeTargetPath(pathMappingList, libPath, false);
            if (localLibPath != null) {
                try {
                    libPaths.addAll(FileUtil.findAllMatchingPaths(localLibPath));
                } catch (IOException e) {
                    logger.error("Error while trying to expand lib path {}", libPath, e);
                    throw new WorkspaceException("Error while trying to expand lib path " + libPath);
                }
            }
        }

        return libPaths;
    }

    private String getLocalPathFromNodeTargetPath(List<NodeWorkspace.PathMappingEntry> pathMappingList, String path,
                                                  Boolean mustBeWritable) {
        for (int i=pathMappingList.size()-1; i>=0; i--) {
            NodeWorkspace.PathMappingEntry entry = pathMappingList.get(i);
            if (path.startsWith(entry.getDestination())) {
                if (!mustBeWritable || !entry.isReadOnly()) {
                    return path.replaceFirst(entry.getDestination(), entry.getSource());
                }
            }
        }

        return null;
    }

    private List<NodeWorkspace.PathMappingEntry> copyOverNodePathsAndMakePathMappingList(Node node, Service nodeService,
                     Path nodeRootDirectory, Map<String, String> compressedToDecompressedMap) throws WorkspaceException {
        List<NodeWorkspace.PathMappingEntry> pathMap = new ArrayList<>();
        try {
            // Copies over node's service paths based on their entry path order
            for (PathEntry pathEntry : nodeService.getApplicationPaths().values().stream()
                    .sorted((p1, p2) -> p1.getOrder().compareTo(p2.getOrder()))
                    .collect(Collectors.toList())) {
                Path sourcePath = Paths.get(pathEntry.shouldBeDecompressed()?
                        compressedToDecompressedMap.get(pathEntry.getPath()):pathEntry.getPath());

                if (pathEntry.shouldCopyOverToWorkspace()) {
                    Path destPath = nodeRootDirectory.resolve(pathToStringWithoutSlashes(pathEntry.getPath()));
                    if (sourcePath.toFile().isDirectory()) {
                        FileUtil.copyDirectory(sourcePath, destPath);
                    } else {
                        Files.copy(sourcePath, destPath,
                                StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    }
                    pathMap.add(new NodeWorkspace.PathMappingEntry(destPath.toString(), pathEntry.getTargetPath(),
                            false));
                } else {
                    pathMap.add(new NodeWorkspace.PathMappingEntry(sourcePath.toString(), pathEntry.getTargetPath(),
                            true));

                }
            }

            // Copies over node's paths based on their entry path order
            for (PathEntry pathEntry : node.getApplicationPaths().values().stream()
                    .sorted((p1, p2) -> p1.getOrder().compareTo(p2.getOrder()))
                    .collect(Collectors.toList())) {
                if (pathEntry.shouldCopyOverToWorkspace()) {
                    Path sourcePath = Paths.get(pathEntry.getPath());
                    Path destPath = nodeRootDirectory.resolve(pathToStringWithoutSlashes(pathEntry.getPath()));
                    if (sourcePath.toFile().isDirectory()) {
                        FileUtil.copyDirectory(sourcePath, destPath);
                    } else {
                        Files.copy(sourcePath, destPath,
                                StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    }
                    pathMap.add(new NodeWorkspace.PathMappingEntry(destPath.toString(), pathEntry.getTargetPath(),
                            false));
                } else {
                    pathMap.add(new NodeWorkspace.PathMappingEntry(pathEntry.getPath(), pathEntry.getTargetPath(),
                            true));
                }
            }

            for (String instrumentablePath: nodeService.getInstrumentablePaths()) {
                // Copies over instrumentable paths if it is not marked as willBeChanged and updates path mapping
                String localInstrumentablePath = getLocalPathFromNodeTargetPath(pathMap,
                        instrumentablePath, true);

                if (localInstrumentablePath == null) {
                    localInstrumentablePath = getLocalPathFromNodeTargetPath(pathMap,
                            instrumentablePath, false);

                    if (localInstrumentablePath == null || !new File(localInstrumentablePath).exists()) {
                        throw new WorkspaceException("The marked instrumentable path `" + nodeService.getInstrumentablePaths() +
                                "` is not marked as willBeChanged or does not exist!");
                    }

                    Path localInstrumentablePathObj = Paths.get(localInstrumentablePath);

                    Path destPath = nodeRootDirectory.resolve("Instrumentable_" + pathToStringWithoutSlashes(
                            instrumentablePath));
                    if (new File(localInstrumentablePath).isDirectory()) {
                        FileUtil.copyDirectory(localInstrumentablePathObj, destPath);
                    } else {
                        Files.copy(localInstrumentablePathObj, destPath,
                                StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    }
                    pathMap.add(new NodeWorkspace.PathMappingEntry(destPath.toString(), instrumentablePath,
                            false));
                }
            }

            return pathMap;
        } catch (IOException e) {
            logger.error("Error in copying over node {} binaries to its workspace!", node.getName(), e);
            throw new WorkspaceException("Error in copying over node " + node.getName() + " binaries to its workspace!");
        }
    }
}
