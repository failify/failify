package me.arminb.spidersilk.workspace;

import java.util.List;

public class NodeWorkspace {
    private final String instrumentableAddress;
    private final String libraryPaths;
    private final String workingDirectory;
    private final String rootDirectory;
    private final String logDirectory;

    public NodeWorkspace(String instrumentableAddress, String libraryPaths, String workingDirectory,
                         String rootDirectory, String logDirectory) {
        this.instrumentableAddress = instrumentableAddress;
        this.libraryPaths = libraryPaths;
        this.workingDirectory = workingDirectory;
        this.rootDirectory = rootDirectory;
        this.logDirectory = logDirectory;
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
}
