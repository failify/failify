package me.arminb.spidersilk.dsl.entities;

public class PathEntry {
    private final String path;
    private final String targetPath;
    private final Boolean isLibrary;
    private final Integer order;

    public PathEntry(String path, String targetPath, Boolean isLibrary, Integer order) {
        this.path = path;
        this.targetPath = targetPath;
        this.isLibrary = isLibrary;
        this.order = order;
    }

    public String getPath() {
        return path;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public Boolean isLibrary() {
        return isLibrary;
    }

    public Integer getOrder() {
        return order;
    }
}
