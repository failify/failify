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

import io.failify.Constants;
import io.failify.util.FileUtil;
import io.failify.dsl.DeploymentEntity;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.*;

/**
 * An abstraction for a service or application inside a distributed system. It is possible to define application paths,
 * environment variables, log files and directories to be collected, ports to be exposed in the container of the node
 * created out of this service (if necessary).
 */
public class Service extends DeploymentEntity {
    private final Map<String, PathEntry> applicationPaths; // map of local paths to absolute target paths for the service
    // Library target paths in the node's container. This is useful when a directory is added as an application path which
    // is not a library but contains sub-paths that are a library
    private final Set<String> libraryPaths;
    private final Set<String> logFiles; // set of target log files to be collected
    private final Set<String> logDirectories; // set of target log directories to be collected
    private final Set<ExposedPortDefinition> exposedPorts; // set of exposed TCP or UDP ports for the node
    private final Map<String, String> environmentVariables; // map of env vars name to value
    private final String dockerImage; // the docker image name and tag to be used for this service
    private final String dockerFileAddress; // the dockerfile address to be used to create the docker image for this service
    private final Boolean dockerImageForceBuild; // the flag to force the build of the dockerfile
    private final Set<String> instrumentablePaths; // the paths that can be changed by the service instrumentors
    private final String initCommand; // the init command of the node which will executed only once
    private final String startCommand; // the start command of the node which will executed when the node is started or restarted
    private final String stopCommand; // the stop command of the node which will executed when the node is stopped or restarted
    private final ServiceType serviceType; // the service programming language
    private Integer pathOrderCounter; // the counter to use for applying order to application paths

    /**
     * Private Constructor
     * @param builder the builder instance to use for creating the class instance
     */
    private Service(Builder builder) {
        super(builder.getName());
        dockerImage = builder.dockerImage;
        dockerFileAddress = builder.dockerFileAddress;
        dockerImageForceBuild = builder.dockerImageForceBuild;
        instrumentablePaths = builder.instrumentablePaths;
        initCommand = builder.initCommand;
        startCommand = builder.startCommand;
        stopCommand = builder.stopCommand;
        serviceType = builder.serviceType;
        applicationPaths = Collections.unmodifiableMap(builder.applicationPaths);
        libraryPaths = Collections.unmodifiableSet(builder.libraryPaths);
        logFiles = Collections.unmodifiableSet(builder.logFiles);
        logDirectories = builder.logDirectories;
        exposedPorts = builder.exposedPorts;
        environmentVariables = Collections.unmodifiableMap(builder.environmentVariables);
        pathOrderCounter = builder.pathOrderCounter;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public Boolean getDockerImageForceBuild() {
        return dockerImageForceBuild;
    }

    public String getDockerFileAddress() {
        return dockerFileAddress;
    }

    public Set<String> getInstrumentablePaths() {
        return instrumentablePaths;
    }

    public String getInitCommand() {
        return initCommand;
    }

    public String getStartCommand() {
        return startCommand;
    }

    public String getStopCommand() {
        return stopCommand;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public Map<String, PathEntry> getApplicationPaths() {
        return applicationPaths;
    }

    public Set<String> getLibraryPaths() {
        return libraryPaths;
    }

    public Set<String> getLogFiles() {
        return logFiles;
    }

    public Set<String> getLogDirectories() {
        return logDirectories;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public Set<ExposedPortDefinition> getExposedPorts() {
        return exposedPorts;
    }

    /**
     * The builder class to build a service object
     */
    public static class Builder extends BuilderBase<Service, Deployment.Builder> {
        private static Logger logger = LoggerFactory.getLogger(Builder.class);

        private Map<String, PathEntry> applicationPaths;
        private Set<String> libraryPaths;
        private Set<String> logFiles;
        private Set<String> logDirectories;
        private Set<ExposedPortDefinition> exposedPorts;
        private Map<String, String> environmentVariables;
        private String dockerImage;
        private String dockerFileAddress;
        private Boolean dockerImageForceBuild;
        private Set<String> instrumentablePaths;
        private String initCommand;
        private String startCommand;
        private String stopCommand;
        private ServiceType serviceType;
        private Integer pathOrderCounter;

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param name the name of the service to be built
         */
        public Builder(Deployment.Builder parentBuilder, String name) {
            super(parentBuilder, name);
            applicationPaths = new HashMap<>();
            instrumentablePaths = new HashSet<>();
            libraryPaths = new HashSet<>();
            logFiles = new HashSet<>();
            logDirectories = new HashSet<>();
            exposedPorts = new HashSet<>();
            environmentVariables = new HashMap<>();
            dockerImage = Constants.DEFAULT_BASE_DOCKER_IMAGE_NAME;
            dockerImageForceBuild = false;
            dockerFileAddress = "Dockerfile-" + name;
            pathOrderCounter = 0;
        }

        /**
         * Constructor
         * @param name the name of the service to be built
         */
        public Builder(String name) {
            this(null, name);
        }

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param instance a service object instance to be changed
         */
        public Builder(Deployment.Builder parentBuilder, Service instance) {
            super(parentBuilder, instance);
            dockerImage = new String(instance.dockerImage);
            dockerFileAddress = new String(instance.dockerFileAddress);
            dockerImageForceBuild = new Boolean(instance.dockerImageForceBuild);
            instrumentablePaths = new HashSet<>(instance.instrumentablePaths);
            initCommand = new String(instance.initCommand);
            startCommand = new String(instance.startCommand);
            stopCommand = new String(instance.stopCommand);
            serviceType = instance.serviceType;
            applicationPaths = new HashMap<>(instance.applicationPaths);
            libraryPaths = new HashSet<>(instance.libraryPaths);
            logFiles = new HashSet<>(instance.logFiles);
            logDirectories = new HashSet<>(instance.logDirectories);
            exposedPorts = new HashSet<>(instance.exposedPorts);
            environmentVariables = new HashMap<>(instance.environmentVariables);
            pathOrderCounter = new Integer(instance.pathOrderCounter);
        }

        /**
         * Constructor
         * @param instance a service object instance to be changed
         */
        public Builder(Service instance) {
            this(null, instance);
        }

        /**
         * Sets the docker image name and tag to be used for this service
         * @param dockerImage the docker image name and tag
         * @return the current builder instance
         */
        public Builder dockerImage(String dockerImage) {
            this.dockerImage = dockerImage;
            return this;
        }

        /**
         * Sets the Dockerfile address to be used to build the docker image for this service
         * @param dockerFileAddress
         * @param forceBuild
         * @return
         */
        public Builder dockerFileAddress(String dockerFileAddress, Boolean forceBuild) {
            this.dockerFileAddress = Paths.get(dockerFileAddress).toAbsolutePath().normalize().toString();
            this.dockerImageForceBuild = forceBuild;
            return this;
        }

        /**
         * Adds an instrumentable path to the service. Instrumentable paths will be marked as changeable and are the only
         * paths that may be changed by an instrumentor
         * @param instrumentablePath an absolute target path in the container of a node created out of this this service to
         *                           be marked as instrumentable
         * @return  the current builder instance
         */
        public Builder instrumentablePath(String instrumentablePath) {
            if (!FileUtil.isPathAbsoluteInUnix(instrumentablePath)) {
                throw new RuntimeException("The instrumentable path `" + instrumentablePath + "` is not absolute!");
            }
            this.instrumentablePaths.add(Paths.get(instrumentablePath).normalize().toString());
            return this;
        }

        /**
         * Sets the start command for the service which will be executed only once
         * @param startCommand the start command of the service
         * @return the current builder instance
         */
        public Builder startCommand(String startCommand) {
            this.startCommand = startCommand;
            return this;
        }

        /**
         * Sets the init command for the service which will be executed only once
         * @param initCommand the init command of the service
         * @return the current builder instance
         */
        public Builder initCommand(String initCommand) {
            this.initCommand = initCommand;
            return this;
        }

        /**
         * Sets the stop command for the service which will be executed only once
         * @param stopCommand the stop command of the service
         * @return the current builder instance
         */
        public Builder stopCommand(String stopCommand) {
            this.stopCommand = stopCommand;
            return this;
        }

        /**
         * Sets the programming language of the service to be used by the instrumentation engine
         * @param serviceType the programming language of the service
         * @return the current builder instance
         */
        public Builder serviceType(ServiceType serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        /**
         * Adds a local path to the specified absolute target path in the node created out of this service and marks it
         * as not changeable, not library and not compressed path
         * @param path a local path
         * @param targetPath an absolute target path in the container of the node created out of this service
         * @return the current builder instance
         */
        public Builder applicationPath(String path, String targetPath) {
            applicationPath(path, targetPath, false, false, false);
            return this;
        }

        /**
         * Adds a local path to the specified absolute target path in the node created out of this service and marks it
         * as not changeable and not compressed path
         * @param path a local path
         * @param targetPath an absolute target path in the container of the node created out of this service
         * @param isLibrary a flag to mark the path as a library to be used by the instrumentation engine
         * @return the current builder instance
         */
        public Builder applicationPath(String path, String targetPath, Boolean isLibrary) {
            applicationPath(path, targetPath, isLibrary, false, false);
            return this;
        }

        /**
         * Adds a local path to the specified absolute target path in the node created out of this service
         * @param path a local path
         * @param targetPath an absolute target path in the container of the node created out of this service
         * @param isLibrary a flag to mark the path as a library to be used by the instrumentation engine
         * @param shouldBeDecompressed a flag to mark the path as compressed to be decompressed before being added to the
         *                             node created out of this service
         * @param willBeChanged a flag to mark the path as changeable which results in a separate copy of the path for
         *                      each node
         * @return the current builder instance
         */
        public Builder applicationPath(String path, String targetPath, Boolean isLibrary, Boolean shouldBeDecompressed,
                                              Boolean willBeChanged) {
            this.applicationPaths.put(path, new PathEntry(
                        path, targetPath, isLibrary, willBeChanged, shouldBeDecompressed, pathOrderCounter++)); // TODO Make this thread-safe
            return this;
        }

        /**
         * Marks an absolute target path in container of a node created out of this service as a library path. This is
         * useful when there is an application path which is not a libray path which has a sub-path that is desired to
         * be a library path
         * @param path an absolute target path to be marked as a library path
         * @return
         */
        public Builder libraryPath(String path) {
            if (!FileUtil.isPathAbsoluteInUnix(path)) {
                throw new RuntimeException("The library path `" + path + "` is not absolute!");
            }
            libraryPaths.add(FilenameUtils.normalizeNoEndSeparator(path, true));
            return this;
        }

        /**
         * Adds an absolute target path in the container of the node created out of this service to be collected as a
         * log file into the node's local workspace
         * @param path an absolute target log file path to be collected
         * @return the current builder instance
         */
        public Builder logFile(String path) {
            if (!FileUtil.isPathAbsoluteInUnix(path)) {
                throw new RuntimeException("The log file `" + path + "` path is not absolute!");
            }
            logFiles.add(FilenameUtils.normalizeNoEndSeparator(path, true));
            return this;
        }

        /**
         * Adds an absolute target path in the container of the node created out of this service to be collected as a
         * log file into the node's local workspace
         * @param path an absolute target log file path to be collected
         * @return the current builder instance
         */
        public Builder logDirectory(String path) {
            if (!FileUtil.isPathAbsoluteInUnix(path)) {
                throw new RuntimeException("The log directory `" + path + "` path is not absolute!");
            }
            this.logDirectories.add(FilenameUtils.normalizeNoEndSeparator(path, true));
            return this;
        }

        /**
         * Adds an environment variable to the service
         * @param name the name of the variable
         * @param value the value of the variable
         * @return the current builder instance
         */
        public Builder environmentVariable(String name, String value) {
            this.environmentVariables.put(name, value);
            return this;
        }

        /**
         * Adds a tcp port to be exposed by the container of a node created out of this service
         * @param portNumber the tcp port number to be exposed
         * @return the current builder instance
         */
        public Builder tcpPort(Integer... portNumber) {
            for (Integer port: portNumber) {
                exposedPorts.add(new ExposedPortDefinition(port, PortType.TCP));
            }
            return this;
        }

        /**
         * Adds a udp port to be exposed by the container of a node created out of this service
         * @param portNumber the udp port number to be exposed
         * @return the current builder instance
         */
        public Builder udpPort(Integer... portNumber) {
            for (Integer port: portNumber) {
                exposedPorts.add(new ExposedPortDefinition(port, PortType.UDP));
            }
            return this;
        }

        public Service build() {
            return new Service(this);
        }

        @Override
        protected void returnToParent(Service builtObj) {
            parentBuilder.service(builtObj);
        }
    }


}
