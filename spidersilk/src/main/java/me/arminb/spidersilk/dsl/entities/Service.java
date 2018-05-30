/*
 * MIT License
 *
 * Copyright (c) 2017 Armin Balalaie
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

package me.arminb.spidersilk.dsl.entities;

import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.dsl.DeploymentEntity;
import me.arminb.spidersilk.exceptions.PathNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * An abstraction for a service or application inside a distributed system.
 */
public class Service extends DeploymentEntity {
    private final Map<String, PathEntry> applicationPaths;
    private final Set<String> libraryPaths;
    private final Set<String> logFiles;
    private final String logFolder;
    private final Map<String, String> environmentVariables;
    private final String dockerImage;
    private final String dockerFileAddress;
    private final Boolean dockerImageForceBuild;
    private final String instrumentableAddress;
    private final String initCommand;
    private final String startCommand;
    private final String stopCommand;
    private final ServiceType serviceType;
    private Integer pathOrderCounter;
    private final String appHomeEnvVar;

    private Service(ServiceBuilder builder) {
        super(builder.getName());
        dockerImage = builder.dockerImage;
        dockerFileAddress = builder.dockerFileAddress;
        dockerImageForceBuild = builder.dockerImageForceBuild;
        instrumentableAddress = builder.instrumentableAddress;
        initCommand = builder.initCommand;
        startCommand = builder.startCommand;
        stopCommand = builder.stopCommand;
        serviceType = builder.serviceType;
        applicationPaths = Collections.unmodifiableMap(builder.applicationPaths);
        libraryPaths = Collections.unmodifiableSet(builder.libraryPaths);
        logFiles = Collections.unmodifiableSet(builder.logFiles);
        logFolder = builder.logFolder;
        environmentVariables = Collections.unmodifiableMap(builder.environmentVariables);
        pathOrderCounter = builder.pathOrderCounter;
        appHomeEnvVar = builder.appHomeEnvVar;
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

    public String getInstrumentableAddress() {
        return instrumentableAddress;
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

    public String getLogFolder() {
        return logFolder;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public String getAppHomeEnvVar() {
        return appHomeEnvVar;
    }

    public static class ServiceBuilder extends DeploymentBuilderBase<Service, Deployment.DeploymentBuilder> {
        private static Logger logger = LoggerFactory.getLogger(ServiceBuilder.class);

        private Map<String, PathEntry> applicationPaths;
        private Set<String> libraryPaths;
        private Set<String> logFiles;
        private String logFolder;
        private Map<String, String> environmentVariables;
        private String dockerImage;
        private String dockerFileAddress;
        private Boolean dockerImageForceBuild;
        private String instrumentableAddress;
        private String initCommand;
        private String startCommand;
        private String stopCommand;
        private ServiceType serviceType;
        private Integer pathOrderCounter;
        private String appHomeEnvVar;


        public ServiceBuilder(Deployment.DeploymentBuilder parentBuilder, String name) {
            super(parentBuilder, name);
            applicationPaths = new HashMap<>();
            libraryPaths = new HashSet<>();
            logFiles = new HashSet<>();
            logFolder = null;
            environmentVariables = new HashMap<>();
            dockerImage = Constants.DEFAULT_BASE_DOCKER_IMAGE_NAME;
            dockerImageForceBuild = false;
            dockerFileAddress = "Dockerfile-" + name;
            pathOrderCounter = 0;
            appHomeEnvVar = null;
        }

        public ServiceBuilder(String name) {
            this(null, name);
        }

        public ServiceBuilder(Deployment.DeploymentBuilder parentBuilder, Service instance) {
            super(parentBuilder, instance);
            dockerImage = new String(instance.dockerImage);
            dockerFileAddress = new String(instance.dockerFileAddress);
            dockerImageForceBuild = new Boolean(instance.dockerImageForceBuild);
            instrumentableAddress = new String(instance.instrumentableAddress);
            initCommand = new String(instance.initCommand);
            startCommand = new String(instance.startCommand);
            stopCommand = new String(instance.stopCommand);
            serviceType = instance.serviceType;
            applicationPaths = new HashMap<>(instance.applicationPaths);
            libraryPaths = new HashSet<>(instance.libraryPaths);
            logFiles = new HashSet<>(instance.logFiles);
            logFolder = new String(instance.logFolder);
            environmentVariables = new HashMap<>(instance.environmentVariables);
            pathOrderCounter = new Integer(instance.pathOrderCounter);
            appHomeEnvVar = new String(instance.appHomeEnvVar);
        }

        public ServiceBuilder(Service instance) {
            this(null, instance);
        }

        public ServiceBuilder dockerImage(String dockerImage) {
            this.dockerImage = dockerImage;
            return this;
        }

        public ServiceBuilder dockerFileAddress(String dockerFileAddress, Boolean forceBuild) {
            this.dockerFileAddress = dockerFileAddress;
            this.dockerImageForceBuild = forceBuild;
            return this;
        }

        public ServiceBuilder relativeInstrumentableAddress(String instrumentableAddress) {
            this.instrumentableAddress = instrumentableAddress;
            return this;
        }

        public ServiceBuilder startCommand(String startCommand) {
            this.startCommand = startCommand;
            return this;
        }

        public ServiceBuilder initCommand(String initCommand) {
            this.initCommand = initCommand;
            return this;
        }

        public ServiceBuilder stopCommand(String stopCommand) {
            this.stopCommand = stopCommand;
            return this;
        }

        public ServiceBuilder serviceType(ServiceType serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        public ServiceBuilder applicationPath(String path) {
            applicationPath(path, false, new File(path).getName());
            return this;
        }

        public ServiceBuilder applicationPath(String path, String targetPath) {
            applicationPath(path, false, targetPath);
            return this;
        }

        public ServiceBuilder applicationPath(String path, Boolean isLibrary) {
            applicationPath(path, isLibrary, new File(path).getName());
            return this;
        }

        public ServiceBuilder applicationPath(String path, Boolean isLibrary, String targetPath) {
            if (!new File(path).exists()) {
                throw new PathNotFoundException(path);
            }

            this.applicationPaths.put(path, new PathEntry(
                        path, targetPath, isLibrary, pathOrderCounter++));
            return this;
        }

        public ServiceBuilder relativeLibraryPath(String path) {
            libraryPaths.add(path);
            return this;
        }

        public ServiceBuilder logFile(String path) {
            logFiles.add(path);
            return this;
        }

        public ServiceBuilder logFolder(String path) {
            this.logFolder = path;
            return this;
        }

        public ServiceBuilder environmentVariable(String name, String value) {
            this.environmentVariables.put(name, value);
            return this;
        }

        public ServiceBuilder exposeAppHomeDirectoryAs(String appHomeEnvVar) {
            this.appHomeEnvVar = appHomeEnvVar;
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
