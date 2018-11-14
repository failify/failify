package me.arminb.spidersilk.workspace;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class NodeWorkspace {

    public static class PathMappingEntry {
        private final String source;
        private final String destination;
        private final Boolean readOnly;

        public PathMappingEntry(String source, String destination, Boolean readOnly) {
            this.source = source;
            this.destination = destination;
            this.readOnly = readOnly;
        }

        public String getSource() {
            return source;
        }

        public String getDestination() {
            return destination;
        }

        public Boolean isReadOnly() {
            return readOnly;
        }
    }

    private final Set<String> instrumentablePaths;
    private final Set<String> libraryPaths;
    private final String workingDirectory;
    private final String rootDirectory;
    private final String logDirectory;
    private final Map<String, String> logDirectoriesMap;
    private final Map<String, String> logFilesMap;
    private final Map<String, String> sharedDirectoriesMap;
    private final List<PathMappingEntry> pathMappingList;


    public NodeWorkspace(Set<String> instrumentablePaths, Set<String> libraryPaths, String workingDirectory,
                         String rootDirectory, String logDirectory, Map<String, String> logDirectoriesMap,
                         Map<String, String> logFilesMap, Map<String, String> sharedDirectoriesMap,
                         List<PathMappingEntry> pathMappingList) {
        this.instrumentablePaths = instrumentablePaths;
        this.libraryPaths = libraryPaths;
        this.workingDirectory = workingDirectory;
        this.rootDirectory = rootDirectory;
        this.logDirectory = logDirectory;
        this.logDirectoriesMap = logDirectoriesMap;
        this.logFilesMap = logFilesMap;
        this.sharedDirectoriesMap = sharedDirectoriesMap;
        this.pathMappingList = pathMappingList;
    }

    public Set<String> getInstrumentablePaths() {
        return instrumentablePaths;
    }

    public Set<String> getLibraryPaths() {
        return libraryPaths;
    }

    public String getRootDirectory() {
        return rootDirectory;
    }

    public String getLogDirectory() {
        return logDirectory;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public Map<String, String> getLogDirectoriesMap() {
        return logDirectoriesMap;
    }

    public Map<String, String> getLogFilesMap() {
        return logFilesMap;
    }

    public Map<String, String> getSharedDirectoriesMap() {
        return sharedDirectoriesMap;
    }

    public List<PathMappingEntry> getPathMappingList() {
        return pathMappingList;
    }
}
