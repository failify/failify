package me.arminb.spidersilk.dsl.entities;

public class PathEntry {
    private final String path;
    private final String targetPath; // this should be a relative path if this is not a shared path entry
    private final Boolean library;
    private final Boolean shared; // if shared the path will not be copied over to the node's workspace
    private final Integer order;

    public PathEntry(String path, String targetPath, Boolean library, Boolean shared, Integer order) {
        this.path = path;
        this.targetPath = targetPath;
        this.library = library;
        this.shared = shared;
        this.order = order;
    }

    public String getPath() {
        return path;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public Boolean isLibrary() {
        return library;
    }

    public Integer getOrder() {
        return order;
    }

    public Boolean isShared() {
        return shared;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!PathEntry.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final PathEntry other = (PathEntry) obj;
        if ((this.path == null) ? (other.path != null) : !this.path.equals(other.path)) {
            return false;
        }
        if ((this.targetPath == null) ? (other.targetPath != null) : !this.targetPath.equals(other.targetPath)) {
            return false;
        }
        if ((this.order == null) ? (other.order != null) : !this.order.equals(other.order)) {
            return false;
        }
        if ((this.shared == null) ? (other.shared != null) : !this.shared.equals(other.shared)) {
            return false;
        }
        if ((this.library == null) ? (other.library != null) : !this.library.equals(other.library)) {
            return false;
        }
        return true;
    }
}
