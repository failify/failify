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
 *
 */

package io.failify.dsl.entities;

import io.failify.util.FileUtil;
import io.failify.exceptions.PathNotFoundException;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

/**
 * All of the application paths defined in each service or node is an instance on this class to be used by the workspace
 * manager to construct the test and nodes workspaces.
 */
public class PathEntry {
    private final String path;
    private final String targetPath; // this should be an absolute path in the node's contaienr
    private final Boolean library; // is this path a library path to be used by the instrumentor
    private final Boolean copyOverToWorkspace; // if copyOverToWorkspace the path will be copied over to the node's workspace
    private final Boolean shouldBeDecompressed; // if this path needs to be decompressed e.g. a zip file
    private final Map<String,String> replacements; // a map of string to string to make replacements in the source file
    private final Integer order; // the order in which the paths will be applied when being added to the container. it is
                                 // important for the overlapping target paths

    /**
     * Constructor
     * @param path the local path to be added to the node's container
     * @param targetPath the target path in the node's container to map the local path to
     * @param replacements a map of string to string to make replacements in the source file
     * @param library if the path is library path to be used by the instrumentor
     * @param copyOverToWorkspace if the path is changeable and should be copied to the node's workspace
     * @param shouldBeDecompressed if the path needs to be decompressed before being added to the node
     * @param order the order in which the paths will be applied when being added to the container. it is important for
     *              the overlapping target paths
     */
    public PathEntry(String path, String targetPath, Map<String, String> replacements, Boolean library,
                     Boolean copyOverToWorkspace, Boolean shouldBeDecompressed, Integer order) {
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
        this.replacements = replacements == null ? null : Collections.unmodifiableMap(replacements);
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

    public Map<String, String> getReplacements() {
        return replacements;
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
        if ((this.replacements == null) ? (other.replacements != null) : !this.replacements.equals(other.replacements)) {
            return false;
        }
        return true;
    }
}
