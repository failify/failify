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
 */

package io.failify.workspace;

import io.failify.Constants;
import io.failify.util.FileUtil;
import io.failify.util.HashingUtil;
import io.failify.util.TarGzipUtil;
import io.failify.dsl.entities.Deployment;
import io.failify.dsl.entities.Node;
import io.failify.dsl.entities.PathEntry;
import io.failify.dsl.entities.Service;
import io.failify.exceptions.WorkspaceException;
import org.apache.commons.io.FileUtils;
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
    private final Path nodesDirectory;
    private final Path logsDirectory;
    private Map<String, String> fakeTimePathMap;
    private Map<String, String> compressedToDecompressedMap;
    private Map<String, String> sharedDirectoriesMap;

    public WorkspaceManager(Deployment deployment) {
        this(deployment, Constants.DEFAULT_WORKING_DIRECTORY_NAME);
    }

    public WorkspaceManager(Deployment deployment, String topLevelWorkingDirectory) {
        this.deployment = deployment;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MMM-dd_HH-mm-ss-SSS");
        this.workingDirectory = Paths.get(".", topLevelWorkingDirectory, deployment.getName() + "_" +
                simpleDateFormat.format(new Date())).toAbsolutePath().normalize();
        this.nodesDirectory = workingDirectory.resolve(Constants.NODES_DIRECTORY_NAME);
        this.logsDirectory = workingDirectory.resolve(Constants.LOGS_DIRECTORY_NAME);
    }

    public Map<String, NodeWorkspace> createWorkspace() throws WorkspaceException {
        Map<String, NodeWorkspace> retMap = new HashMap<>();

        // Creates the working directory
        try {
            logger.info("Creating the working directory at {}", workingDirectory.toString());
            Files.createDirectories(workingDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating Failify working directory at " + workingDirectory.toString(), e);
        }

        // Creates the nodes directory
        try {
            Files.createDirectories(nodesDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating Failify nodes directory at " + nodesDirectory.toString(), e);
        }

        // Creates the logs directory
        try {
            Files.createDirectories(logsDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating Failify logs directory at " + logsDirectory.toString(), e);
        }

        // Creates the shared directories
        sharedDirectoriesMap = createSharedDirectories();

        // Decompress compressed application paths in services
        compressedToDecompressedMap = decompressCompressedApplicationPaths();

        // Copies over libfaketime binaries to the working directory
        fakeTimePathMap = copyOverLibFakeTime(workingDirectory);

        // Creates the nodes' workspaces
        for (Node node: deployment.getNodes().values()) {
            retMap.put(node.getName(), createNodeWorkspace(node));
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
            throw new WorkspaceException("Error in creating shared directories root directory", e);
        }

        for (String path: deployment.getSharedDirectories()) {

            // Creating the shared directories
            try {
                Path sharedDirectory = sharedDirectoriesRoot.resolve(pathToStringWithoutSlashes(path));
                Files.createDirectory(sharedDirectory);
                sharedDirectoriesMap.put(sharedDirectory.toString(), path);
            } catch (IOException e) {
                throw new WorkspaceException("Error in creating shared directory " + path, e);
            }
        }

        return sharedDirectoriesMap;
    }

    private Map<String, String> decompressCompressedApplicationPaths() throws WorkspaceException {
        Map<String, String> retMap = new HashMap<>();
        Path decompressedDirectory = workingDirectory.resolve(Constants.DECOMPRESSED_DIRECTORIES_ROOT_NAME);

        try {
            Files.createDirectory(decompressedDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating decompressed directory at " + decompressedDirectory, e);
        }

        for (Service service: deployment.getServices().values()) {
            for (PathEntry pathEntry: service.getApplicationPaths().values()) {
                if (pathEntry.shouldBeDecompressed() && !retMap.containsKey(pathEntry.getPath())) {
                    logger.info("Decompressing {} ...", pathEntry.getPath());
                    File targetDir = decompressedDirectory.resolve(pathToStringWithoutSlashes(pathEntry.getPath())).toFile();
                    targetDir.mkdirs();
                    if (pathEntry.getPath().endsWith(".zip")) {
                        try {
                            TarGzipUtil.unzip(pathEntry.getPath(), targetDir.toString());
                        } catch (IOException e) {
                            throw new WorkspaceException("Error while unzipping " + pathEntry.getPath(), e);
                        }
                    } else if (pathEntry.getPath().endsWith(".tar.gz") || pathEntry.getPath().endsWith(".tgz")) {
                        try {
                            TarGzipUtil.unTarGzip(pathEntry.getPath(), targetDir.toString());
                        } catch (IOException e) {
                            throw new WorkspaceException("Error while extracting " + pathEntry.getPath(), e);
                        }
                    } else {
                        throw new WorkspaceException("Decompression is only supported for zip files!"
                                + pathEntry.getPath() + " is not a zip file.");
                    }
                    retMap.put(pathEntry.getPath(), targetDir.toString());
                }
            }
        }
        return retMap;
    }

    // TODO should this be public?
    public NodeWorkspace createNodeWorkspace(Node node)
            throws WorkspaceException {
        logger.info("Creating workspace for node {}", node.getName());

        // Creates the node's working directory
        Path nodeWorkingDirectory = nodesDirectory.resolve(node.getName());
        try {
            Files.createDirectory(nodeWorkingDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating Failify node working directory \""
                    + node.getName() + "\"!", e);
        }

        // Creates the node root directory
        Path nodeRootDirectory = nodeWorkingDirectory.resolve(Constants.NODE_ROOT_DIRECTORY_NAME);
        try {
            Files.createDirectory(nodeRootDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating Failify node root directory \""
                    + node.getName() + "\"!", e);
        }

        // Creates the node's log directory
        Path nodeLogDirectory = logsDirectory.resolve(node.getName());
        try {
            Files.createDirectory(nodeLogDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating Failify node log directory \""
                    + node.getName() + "\"!", e);
        }

        // Creates the node's log files
        Map<String, String> logFilesMap = createLogFiles(node, nodeLogDirectory);

        // Creates the node's log directories
        Map<String, String> logDirectoriesMap = createLogDirectories(node, nodeLogDirectory);

        Service nodeService = deployment.getService(node.getServiceName());

        Set<String> instrumentablesPathSet = new HashSet<>();
        // Copies over the node paths to the node root directory
        List<NodeWorkspace.PathMappingEntry> pathMappingList = copyOverNodePathsAndMakePathMappingList(node, nodeService,
                nodeRootDirectory, compressedToDecompressedMap, instrumentablesPathSet);

        // Adds fakeTimeLib paths to the path mapping
        fakeTimePathMap.entrySet().stream().forEach(e -> pathMappingList.add(
                new NodeWorkspace.PathMappingEntry(e.getKey(), e.getValue(), true)));

        // Creates the node workspace object
        return new NodeWorkspace(
                instrumentablesPathSet,
                getNodeLibPaths(nodeService, pathMappingList),
                nodeWorkingDirectory.toString(),
                nodeRootDirectory.toString(),
                nodeLogDirectory.toString(),
                logDirectoriesMap,
                logFilesMap,
                sharedDirectoriesMap,
                pathMappingList);
    }

    private Map<String, String> createLogFiles(Node node, Path nodeLogDirectory) throws WorkspaceException {
        Map<String, String> logFilesMap = new HashMap<>();

        for (String path: getNodeLogFiles(node)) {
            Path logFile = nodeLogDirectory.resolve(pathToStringWithoutSlashes(path));
            try {
                logFile.toFile().createNewFile();
                logFilesMap.put(logFile.toString(), path);
            } catch (IOException e) {
                throw new WorkspaceException("Error while creating log file " + logFile, e);
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
                throw new WorkspaceException("Error while creating log directory " + logDirectory, e);
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
                        throw new WorkspaceException("Error while trying to expand lib path " + pathEntry.getTargetPath(), e);
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
                    throw new WorkspaceException("Error while trying to expand lib path " + libPath, e);
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
                } else {
                    return null;
                }
            }
        }

        return null;
    }

    private String[] getLocalPathAndMatchedPathFromNodeTargetPath(List<NodeWorkspace.PathMappingEntry> pathMappingList, String path,
            Boolean mustBeWritable) {
        for (int i=pathMappingList.size()-1; i>=0; i--) {
            NodeWorkspace.PathMappingEntry entry = pathMappingList.get(i);
            if (path.startsWith(entry.getDestination())) {
                if (!mustBeWritable || !entry.isReadOnly()) {
                    return new String[] {path.replaceFirst(entry.getDestination(), entry.getSource()), entry.getSource(), entry.getDestination()};
                } else {
                    return null;
                }
            }
        }

        return null;
    }

    private List<NodeWorkspace.PathMappingEntry> copyOverNodePathsAndMakePathMappingList(Node node, Service nodeService,
            Path nodeRootDirectory, Map<String, String> compressedToDecompressedMap, Set<String> localInstrumentablePathSet)
            throws WorkspaceException {
        List<NodeWorkspace.PathMappingEntry> pathMapList = new ArrayList<>();
        Map<String, NodeWorkspace.PathMappingEntry> pathMap = new HashMap<>();

        NodeWorkspace.PathMappingEntry pathMappingEntry;
        try {
            // Copies over node's service paths based on their entry path order
            Iterator<Map.Entry<String, PathEntry>> servicePathsIterator =  nodeService.getApplicationPaths().entrySet().iterator();
            while (servicePathsIterator.hasNext()) {
                PathEntry pathEntry = servicePathsIterator.next().getValue();
                // TODO if parent path exists in the path mappings, first subpath should be removed from the node workspace and then the new should be copied over
                String copiedPath = copyOverPathEntry(pathEntry, compressedToDecompressedMap, nodeRootDirectory, nodeService.getName());

                pathMappingEntry = new NodeWorkspace.PathMappingEntry(copiedPath, pathEntry.getTargetPath(),
                        !pathEntry.shouldCopyOverToWorkspace());
                pathMapList.add(pathMappingEntry);
                pathMap.put(pathEntry.getTargetPath(), pathMappingEntry);
            }

            // Copies over node's paths based on their entry path order
            Iterator<Map.Entry<String, PathEntry>> nodePathsIterator =  node.getApplicationPaths().entrySet().iterator();
            while (nodePathsIterator.hasNext()) {
                PathEntry pathEntry = nodePathsIterator.next().getValue();
                String copiedPath = copyOverPathEntry(pathEntry, null, nodeRootDirectory, node.getName());

                pathMappingEntry = new NodeWorkspace.PathMappingEntry(copiedPath, pathEntry.getTargetPath(),
                        !pathEntry.shouldCopyOverToWorkspace());
                pathMapList.add(pathMappingEntry);
                pathMap.put(pathEntry.getTargetPath(), pathMappingEntry);
            }

            if (deployment.isNodeInRunSequence(node)) {
                for (String instrumentablePath: nodeService.getInstrumentablePaths()) {
                    // Copies over instrumentable paths if it is not marked as willBeChanged and updates path mapping
                    String localInstrumentablePath =
                            getLocalPathFromNodeTargetPath(pathMapList, instrumentablePath, true);

                    if (localInstrumentablePath == null) {
                        String[] localPathAndMatchedPath = getLocalPathAndMatchedPathFromNodeTargetPath(pathMapList, instrumentablePath, false);

                        if (localPathAndMatchedPath == null) {
                            throw new WorkspaceException("The marked instrumentable path `" + instrumentablePath +
                                    "` does not exist!");
                        }

                        localInstrumentablePath = localPathAndMatchedPath[0];
                        String matchedLocal = localPathAndMatchedPath[1];
                        String matchedTarget = localPathAndMatchedPath[2];

                        for (String exPath: FileUtil.findAllMatchingPaths(localInstrumentablePath)) {
                            Path exPathObj = Paths.get(exPath);

                            Path destPath = nodeRootDirectory.resolve("Instrumentable_" + HashingUtil.md5(exPath) + exPathObj.toFile().getName());
                            if (new File(exPath).isDirectory()) {
                                FileUtil.copyDirectory(exPathObj, destPath);
                            } else {
                                Files.copy(exPathObj, destPath, StandardCopyOption.COPY_ATTRIBUTES,
                                        StandardCopyOption.REPLACE_EXISTING);
                            }

                            String newInstrumentablePath = exPath.replaceFirst(matchedLocal, matchedTarget);

                            pathMappingEntry = new NodeWorkspace.PathMappingEntry(destPath.toString(), newInstrumentablePath, false);
                            pathMap.put(newInstrumentablePath, pathMappingEntry);
                            pathMapList.add(pathMappingEntry);
                            localInstrumentablePathSet.add(destPath.toString());
                        }
                    } else {
                        // A willBeChanged path is found, so extend the path and add all of them to the instrumentablePaths
                        localInstrumentablePathSet.addAll(FileUtil.findAllMatchingPaths(localInstrumentablePath));
                    }
                }
            }

            // after this stage these mappings will be used by docker and the order is not important
            return pathMapList;
        } catch (IOException e) {
            throw new WorkspaceException("Error in copying over node " + node.getName() + " binaries to its workspace!", e);
        }
    }

    private String copyOverPathEntry(PathEntry pathEntry, Map<String, String> compressedToDecompressedMap,
                                                             Path nodeRootDirectory, String nodeOrServiceName) throws IOException {
        Path sourcePath;
        if (pathEntry.getReplacements() == null) {
            if (compressedToDecompressedMap != null) {
                sourcePath = Paths.get(pathEntry.shouldBeDecompressed() ?
                        compressedToDecompressedMap.get(pathEntry.getPath()) : pathEntry.getPath());
            } else {
                sourcePath = Paths.get(pathEntry.getPath());
            }
        } else {
            sourcePath = performFileReplacements(pathEntry.getPath(), pathEntry.getReplacements(), nodeOrServiceName);
        }

        if (pathEntry.shouldCopyOverToWorkspace()) {
            Path destPath = nodeRootDirectory.resolve(pathToStringWithoutSlashes(pathEntry.getPath()));
            if (sourcePath.toFile().isDirectory()) {
                FileUtil.copyDirectory(sourcePath, destPath);
            } else {
                Files.copy(sourcePath, destPath,
                        StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            }
            return destPath.toString();
        } else {
            return sourcePath.toString();
        }
    }

    private Path performFileReplacements(String source, Map<String, String> replacements, String nodeOrServiceName) throws IOException {
        Path replacementDirectory = workingDirectory.resolve(Constants.REPLACEMENT_DIRECTORY_NAME).resolve(nodeOrServiceName);
        Files.createDirectories(replacementDirectory);

        Path replacementFile = replacementDirectory.resolve(pathToStringWithoutSlashes(source));
        Files.copy(Paths.get(source), replacementFile,
                StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);

        String content = new String(Files.readAllBytes(replacementFile));
        for (String key: replacements.keySet()) {
            content = content.replace("{{" + key + "}}", replacements.get(key));
        }

        Files.write(replacementFile, content.getBytes());

        return replacementFile;
    }

    private Map<String, String> copyOverLibFakeTime(Path workingDirectory) throws WorkspaceException {

        Map<String,String> pathMap = new HashMap<>();

        // Creates the faketime lib directory
        Path fakeTimeLibDirectory = workingDirectory.resolve(Constants.FAKETIME_DIRECTORY_NAME);
        try {
            Files.createDirectory(fakeTimeLibDirectory);
        } catch (IOException e) {
            throw new WorkspaceException("Error in creating Failify faketime lib directory!", e);
        }

        String[] filesToBeCopied = {Constants.FAKETIME_LIB_FILE_NAME, Constants.FAKETIMEMT_LIB_FILE_NAME};

        try {
            for (String fileToBeCopied: filesToBeCopied) {
                Path fakeTimePath = fakeTimeLibDirectory.resolve(fileToBeCopied);
                Files.copy(Thread.currentThread().getContextClassLoader().getResourceAsStream(fileToBeCopied),
                        fakeTimePath);
                pathMap.put(fakeTimePath.toString(), Constants.FAKETIME_TARGET_BASE_PATH  + fileToBeCopied);
            }
        } catch (IOException e) {
            throw new WorkspaceException("Error in copying over faketime lib binaries to the workspace!", e);
        }

        return pathMap;
    }

    public void cleanUp() {
        File decompressedDirectory = workingDirectory.resolve(Constants.DECOMPRESSED_DIRECTORIES_ROOT_NAME).toFile();
        if (decompressedDirectory.exists()) {
            try {
                FileUtils.deleteDirectory(decompressedDirectory);
            } catch (IOException e) {
                logger.warn("Error while deleting the decompressed directory");
            }
        }
        File fakeTimeDirectory = workingDirectory.resolve(Constants.FAKETIME_DIRECTORY_NAME).toFile();
        if (fakeTimeDirectory.exists()) {
            try {
                FileUtils.deleteDirectory(fakeTimeDirectory);
            } catch (IOException e) {
                logger.warn("Error while deleting the fakeTime directory");
            }
        }
    }
}
