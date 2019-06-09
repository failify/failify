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
import io.failify.execution.ULimit;
import io.failify.util.FileUtil;
import io.failify.dsl.DeploymentEntity;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

/**
 * An abstraction for a service or application inside a distributed system. It is possible to define application paths,
 * environment variables, log files and directories to be collected, ports to be exposed in the container of the node
 * created out of this service (if necessary).
 */
public class Service extends DeploymentEntity {
    private final Map<String, PathEntry> applicationPaths; // map of target paths to path entries for the service
    // Library target paths in the node's container. This is useful when a directory is added as an application path which
    // is not a library but contains sub-paths that are a library
    private final Set<String> libraryPaths;
    private final Set<String> logFiles; // set of target log files to be collected
    private final Set<String> logDirectories; // set of target log directories to be collected
    private final Set<ExposedPortDefinition> exposedPorts; // set of exposed TCP or UDP ports for the node
    private final Map<String, String> environmentVariables; // map of env vars name to value
    private final String dockerImage; // the docker image name and tag to be used for this service
    private final String dockerFile; // the dockerfile address to be used to create the docker image for this service
    private final Boolean dockerImageForceBuild; // the flag to force the build of the dockerfile
    private final Set<String> instrumentablePaths; // the paths that can be changed by the service instrumentors
    private final String initCommand; // the init command of the node which will executed only once
    private final String startCommand; // the start command of the node which will executed when the node is started or restarted
    private final String stopCommand; // the stop command of the node which will executed when the node is stopped or restarted
    private final ServiceType serviceType; // the service programming language
    private final Boolean disableClockDrift; // the flag to disable clock drift capability
    private Integer pathOrderCounter; // the counter to use for applying order to application paths
    private final String workDir; // the working directory in the container
    private final Map<ULimit, ULimit.Values> ulimits; // the ulimits map for the container

    /**
     * Private Constructor
     * @param builder the builder instance to use for creating the class instance
     */
    private Service(Builder builder) {
        super(builder.getName());
        dockerImage = builder.dockerImage;
        dockerFile = builder.dockerFile;
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
        disableClockDrift = builder.disableClockDrift;
        workDir = builder.workDir;
        ulimits = Collections.unmodifiableMap(builder.ulimits);
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public Boolean getDockerImageForceBuild() {
        return dockerImageForceBuild;
    }

    public String getDockerFile() {
        return dockerFile;
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

    public Boolean isClockDriftEnabled() {
        return !disableClockDrift;
    }

    public String getWorkDir() {
        return workDir;
    }

    public Map<ULimit, ULimit.Values> ulimits() {
        return ulimits;
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
        private String dockerFile;
        private Boolean dockerImageForceBuild;
        private Set<String> instrumentablePaths;
        private String initCommand;
        private String startCommand;
        private String stopCommand;
        private Boolean disableClockDrift;
        private ServiceType serviceType;
        private Integer pathOrderCounter;
        private String workDir;
        private Map<ULimit, ULimit.Values> ulimits;

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
            dockerFile = null;
            pathOrderCounter = 0;
            serviceType = ServiceType.OTHER;
            disableClockDrift = false;
            workDir = null;
            ulimits = new HashMap<>();
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
            dockerFile = new String(instance.dockerFile);
            dockerImageForceBuild = new Boolean(instance.dockerImageForceBuild);
            instrumentablePaths = new HashSet<>(instance.instrumentablePaths);
            initCommand = instance.initCommand == null ? null : new String(instance.initCommand);
            startCommand = instance.startCommand == null ? null : new String(instance.startCommand);
            stopCommand = instance.stopCommand == null ? null : new String(instance.stopCommand);
            serviceType = instance.serviceType;
            applicationPaths = new HashMap<>(instance.applicationPaths);
            libraryPaths = new HashSet<>(instance.libraryPaths);
            logFiles = new HashSet<>(instance.logFiles);
            logDirectories = new HashSet<>(instance.logDirectories);
            exposedPorts = new HashSet<>(instance.exposedPorts);
            environmentVariables = new HashMap<>(instance.environmentVariables);
            pathOrderCounter = new Integer(instance.pathOrderCounter);
            disableClockDrift = new Boolean(instance.disableClockDrift);
            workDir = instance.workDir == null ? null : new String(instance.workDir);
            ulimits = new HashMap<>(instance.ulimits);
        }

        /**
         * Constructor for creating a service builder out of an existing instance but with a new name
         * @param parentBuilder the parent builder object for this builder
         * @param instance a service object instance to be changed
         */
        public Builder(Deployment.Builder parentBuilder, String newName, Service instance) {
            super(parentBuilder, newName);
            dockerImage = new String(instance.dockerImage);
            dockerFile = new String(instance.dockerFile);
            dockerImageForceBuild = new Boolean(instance.dockerImageForceBuild);
            instrumentablePaths = new HashSet<>(instance.instrumentablePaths);
            initCommand = instance.initCommand == null ? null : new String(instance.initCommand);
            startCommand = instance.startCommand == null ? null : new String(instance.startCommand);
            stopCommand = instance.stopCommand == null ? null : new String(instance.stopCommand);
            serviceType = instance.serviceType;
            applicationPaths = new HashMap<>(instance.applicationPaths);
            libraryPaths = new HashSet<>(instance.libraryPaths);
            logFiles = new HashSet<>(instance.logFiles);
            logDirectories = new HashSet<>(instance.logDirectories);
            exposedPorts = new HashSet<>(instance.exposedPorts);
            environmentVariables = new HashMap<>(instance.environmentVariables);
            pathOrderCounter = new Integer(instance.pathOrderCounter);
            disableClockDrift = new Boolean(instance.disableClockDrift);
            workDir = instance.workDir == null ? null : new String(instance.workDir);
            ulimits = new HashMap<>(instance.ulimits);
        }

        /**
         * Constructor
         * @param instance a service object instance to be changed
         */
        public Builder(Service instance) {
            this(null, instance);
        }

        /**
         * Sets the docker image name and tag to be used for this service. If dockerfile address is set for this service,
         * this will be the name of the build docker image out of the dockerfile.
         * @param dockerImage the docker image name and tag
         * @return the current builder instance
         */
        public Builder dockerImg(String dockerImage) {
            this.dockerImage = dockerImage;
            return this;
        }

        /**
         * Sets the Dockerfile address to be used to build the docker image for this service
         * @param dockerFileAddress
         * @param forceBuild
         * @return
         */
        public Builder dockerFile(String dockerFileAddress, Boolean forceBuild) {
            this.dockerFile = Paths.get(dockerFileAddress).toAbsolutePath().normalize().toString();
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

            instrumentablePath = FilenameUtils.normalizeNoEndSeparator(instrumentablePath, true);

            this.instrumentablePaths.add(Paths.get(instrumentablePath).normalize().toString());
            return this;
        }

        /**
         * Sets the start command for the service which will be executed only once
         * @param startCommand the start command of the service
         * @return the current builder instance
         */
        public Builder startCmd(String startCommand) {
            this.startCommand = startCommand;
            return this;
        }

        /**
         * Sets the init command for the service which will be executed only once
         * @param initCommand the init command of the service
         * @return the current builder instance
         */
        public Builder initCmd(String initCommand) {
            this.initCommand = initCommand;
            return this;
        }

        /**
         * Sets the stop command for the service which will be executed only once
         * @param stopCommand the stop command of the service
         * @return the current builder instance
         */
        public Builder stopCmd(String stopCommand) {
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
         * Adds a local path to the specified absolute target path in the node created out of this service
         * @param path a local path
         * @param targetPath an absolute target path in the container of the node created out of this service
         * @param pathAttrs the attributes of the path. PathAttr.LIBRARY marks the path as a library to be used by the
         *                  instrumentation engine. PathAttr.COMPRESSED marks the path as compressed to be decompressed
         *                  before being added to the node created out of this service. PathAttr.CHANGEABLE marks the path
         *                  as changeable which results in a separate copy of the path for each node.
         * @return the current builder instance
         */
        public Builder appPath(String path, String targetPath, PathAttr... pathAttrs) {
            return appPath(path, targetPath, null, pathAttrs);
        }

        /**
         * Adds a local path to the specified absolute target path in the node created out of this service
         * @param path a local path
         * @param targetPath an absolute target path in the container of the node created out of this service
         * @param pathAttrs the attributes of the path. PathAttr.LIBRARY marks the path as a library to be used by the
         *                  instrumentation engine. PathAttr.COMPRESSED marks the path as compressed to be decompressed
         *                  before being added to the node created out of this service. PathAttr.CHANGEABLE marks the path
         *                  as changeable which results in a separate copy of the path for each node.
         * @param replacements a map of string to string where all keys in the form of ``{{key}}`` will be replaced by the
         *                     corresponding value in the local path. If not null, a new file will be generated with the
         *                     replaced values. This option can only be used when the local path is file, is not a library
         *                     and is not going to be decompressed.
         * @return the current builder instance
         */
        public Builder appPath(String path, String targetPath, Map<String, String> replacements, PathAttr... pathAttrs) {
            boolean isLibrary = false, willBeChanged = false, shouldBeDecompressed = false;

            for (PathAttr pathAttr: pathAttrs) {
                switch (pathAttr) {
                    case LIBRARY:
                        isLibrary = true; break;
                    case CHANGEABLE:
                        willBeChanged = true; break;
                    case COMPRESSED:
                        shouldBeDecompressed = true; break;
                }
            }

            if (replacements != null && (!new File(path).isFile() || isLibrary || shouldBeDecompressed)) {
                throw new RuntimeException("Replacements map only works when the source path is a non-library" +
                        " and non-compressed file!");
            }

            targetPath = FilenameUtils.normalizeNoEndSeparator(targetPath, true);

            this.applicationPaths.put(targetPath, new PathEntry(
                    path, targetPath, replacements, isLibrary, willBeChanged, shouldBeDecompressed, pathOrderCounter++)); // TODO Make this thread-safe
            return this;
        }

        /**
         * Marks an absolute target path in container of a node created out of this service as a library path. This is
         * useful when there is an application path which is not a libray path which has a sub-path that is desired to
         * be a library path
         * @param path an absolute or wildcard target path to be marked as a library path
         * @return
         */
        public Builder libPath(String path) {
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
        public Builder logDir(String path) {
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
        public Builder env(String name, String value) {
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
         * The clock drift capability is being supported through the libfaketime library. This library has limitations and
         * may cause unexpected errors with some binaries. If you are seeing unexpected error messages that you normally
         * don't see, you should try disabling clock drift capability by calling this method.
         * @return the current builder instance
         */
        public Builder disableClockDrift() {
            this.disableClockDrift = true;
            return this;
        }

        /**
         * Enables clock drift capability (enabled by default. Only call this if you have disabled it somewhere else)
         * @return the current builder instance
         */
        public Builder enableClockDrift() {
            this.disableClockDrift = false;
            return this;
        }

        /**
         * Sets the working directory for the container. This can help minimize the start, stop and init commands. Also,
         * it can help when running a command inside container.
         * @param workDir the working directory
         * @return the current builder instance
         */
        public Builder workDir(String workDir) {
            this.workDir = workDir;
            return this;
        }

        /**
         * Creates a ulimit for the node container using the given parameters. for unlimited use -1
         * @param soft the soft limit
         * @param hard the hard limit
         * @return the current builder instance
         */
        public Builder ulimit(ULimit name, long soft, long hard) {
            if (hard != -1 && soft > hard) hard = soft;
            ulimits.put(name, new ULimit.Values(soft, hard));
            return this;
        }

        /**
         * Creates a ulimit for the node container using the given parameter. for unlimited use -1.
         * The same value will be used for hard limit
         * @param soft the soft limit
         * @return the current builder instance
         */
        public Builder ulimit(ULimit name, long soft) {
            ulimits.put(name, new ULimit.Values(soft, soft));
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
