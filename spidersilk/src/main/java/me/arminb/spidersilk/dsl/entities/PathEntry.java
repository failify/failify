package me.arminb.spidersilk.dsl.entities;

import me.arminb.spidersilk.exceptions.PathNotFoundException;
import me.arminb.spidersilk.util.FileUtil;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Paths;

public class PathEntry {
    private final String path;
    private final String targetPath; // this should be a relative path if this is a copyOverToWorkspace path entry
    private final Boolean library;
    private final Boolean copyOverToWorkspace; // if copyOverToWorkspace the path will be copied over to the node's workspace
    private final Boolean shouldBeDecompressed;
    private final Integer order;

    public PathEntry(String path, String targetPath, Boolean library, Boolean copyOverToWorkspace, Boolean shouldBeDecompressed,
                     Integer order) {
        if (!new File(path).exists()) {
            throw new PathNotFoundException(path);
        }

        path = Paths.get(path).toAbsolutePath().normalize().toString();

        this.path = path;

        if (!FileUtil.isPathAbsoluteInUnix(targetPath)) {
            throw new RuntimeException("The target path `" + path + "` is not absolute!");
        }

        targetPath = FilenameUtils.normalizeNoEndSeparator(targetPath, true);

        this.targetPath = targetPath;
        this.library = library;
        this.copyOverToWorkspace = copyOverToWorkspace;
        this.shouldBeDecompressed = shouldBeDecompressed;
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

    public Boolean shouldCopyOverToWorkspace() {
        return copyOverToWorkspace;
    }

    public Boolean shouldBeDecompressed() {
        return shouldBeDecompressed;
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
        if ((this.copyOverToWorkspace == null) ? (other.copyOverToWorkspace != null) : !this.copyOverToWorkspace.equals(other.copyOverToWorkspace)) {
            return false;
        }
        if ((this.library == null) ? (other.library != null) : !this.library.equals(other.library)) {
            return false;
        }
        return true;
    }
}
