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

package io.failify.util;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerMount;
import io.failify.exceptions.RuntimeEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class DockerUtil {
    private final static Logger logger = LoggerFactory.getLogger(DockerUtil.class);

    private static Map<String, Map<String,String>> containerIdToVolumeMappingCache;

    static {
        containerIdToVolumeMappingCache = new HashMap<>();
    }

    private static Map<String, String> getDockerVolumeMapping(DockerClient dockerClient, String containerId)
            throws DockerException, InterruptedException {

        // If there is an entry in the cache return that one
        if (containerIdToVolumeMappingCache.containsKey(containerId)) {
            return containerIdToVolumeMappingCache.get(containerId);
        }

        Map<String, String> volumeMap = new HashMap<>();

        ContainerInfo containerInfo = dockerClient.inspectContainer(containerId);
        for (ContainerMount containerMount: containerInfo.mounts()) {
            String source = containerMount.source();
            String destination = containerMount.destination();

            // TODO is this cross platform ?
            if (source.endsWith("/")) {
                source = source.substring(0, source.length() - 1);
            }

            if (destination.endsWith("/")) {
                destination = destination.substring(0, destination.length() - 1);
            }

            volumeMap.put(destination, source);
        }

        // Populate the cache for the next time
        containerIdToVolumeMappingCache.put(containerId, volumeMap);

        return volumeMap;
    }

    public static String mapDockerPathToHostPath(
            DockerClient dockerClient, String containerId, String path) throws RuntimeEngineException {

        if (containerId == null) {
            return path;
        }

        Map<String, String> dockerVolumeMap = null;
        try {
            dockerVolumeMap = getDockerVolumeMapping(dockerClient, containerId);
        } catch (InterruptedException | DockerException e) {
            throw new RuntimeEngineException("Error while getting mount points of container " + containerId, e);
        }

        if (dockerVolumeMap.isEmpty()) {
            return path;
        }

        for (int i=path.length(); i>0; i--) {
            String subPath = path.substring(0,i);
            if (dockerVolumeMap.containsKey(subPath)) {
                return path.replaceFirst(subPath, dockerVolumeMap.get(subPath));
            }
        }

        return path;
    }

    public static Boolean isRunningInsideDocker() {
        try (Stream< String > stream = Files.lines(Paths.get("/proc/1/cgroup"))) {
            return stream.anyMatch(line -> line.contains("docker"));
        } catch (IOException e) {
            return false;
        }
    }

    public static String getMyContainerId() throws IOException {
        if (!isRunningInsideDocker()) {
            return null;
        }

        String procFileFirstLine;

        try (Stream< String > stream = Files.lines(Paths.get("/proc/1/cgroup"))) {
            procFileFirstLine = stream.filter(line -> line.contains("docker")).findFirst().orElse(null);
        }

        if (procFileFirstLine == null || procFileFirstLine.isEmpty()) {
            return null;
        }

        int lastSlashIndex = procFileFirstLine.lastIndexOf("/");
        if (lastSlashIndex == -1) {
            return null;
        }

        return procFileFirstLine.substring(lastSlashIndex + 1);
    }
}
