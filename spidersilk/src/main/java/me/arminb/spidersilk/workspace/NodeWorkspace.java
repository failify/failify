package me.arminb.spidersilk.workspace;

import java.util.Map;

public class NodeWorkspace {
    private final String instrumentableAddress;
    private final String libraryPaths;
    private final String workingDirectory;
    private final String rootDirectory;
    private final String logDirectory;
    private final Map<String, String> logDirectoriesMap;
    private final Map<String, String> logFilesMap;
    private final Map<String, String> sharedDirectoriesMap;

    public NodeWorkspace(String instrumentableAddress, String libraryPaths, String workingDirectory,
                         String rootDirectory, String logDirectory, Map<String, String> logDirectoriesMap, Map<String, String> logFilesMap, Map<String, String> sharedDirectoriesMap) {
        this.instrumentableAddress = instrumentableAddress;
        this.libraryPaths = libraryPaths;
        this.workingDirectory = workingDirectory;
        this.rootDirectory = rootDirectory;
        this.logDirectory = logDirectory;
        this.logDirectoriesMap = logDirectoriesMap;
        this.logFilesMap = logFilesMap;
        this.sharedDirectoriesMap = sharedDirectoriesMap;
    }

    public String getInstrumentableAddress() {
        return instrumentableAddress;
    }

    public String getLibraryPaths() {
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
}
