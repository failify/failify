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
import me.arminb.spidersilk.util.PathUtil;
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
    private final Map<String, String> environmentVariables;
    private final String dockerImage;
    private final String instrumentableAddress;
    private final String runCommand;
    private final ServiceType serviceType;
    private Integer pathOrderCounter;

    private Service(ServiceBuilder builder) {
        super(builder.getName());
        dockerImage = builder.dockerImage;
        instrumentableAddress = builder.instrumentableAddress;
        runCommand = builder.runCommand;
        serviceType = builder.serviceType;
        applicationPaths = Collections.unmodifiableMap(builder.applicationPaths);
        libraryPaths = Collections.unmodifiableSet(builder.libraryPaths);
        environmentVariables = Collections.unmodifiableMap(builder.environmentVariables);
        pathOrderCounter = builder.pathOrderCounter;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public String getInstrumentableAddress() {
        return instrumentableAddress;
    }

    public String getRunCommand() {
        return runCommand;
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

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public static class ServiceBuilder extends DeploymentBuilderBase<Service, Deployment.DeploymentBuilder> {
        private static Logger logger = LoggerFactory.getLogger(ServiceBuilder.class);

        private Map<String, PathEntry> applicationPaths;
        private Set<String> libraryPaths;
        private Map<String, String> environmentVariables;
        private String dockerImage;
        private String instrumentableAddress;
        private String runCommand;
        private ServiceType serviceType;
        private Integer pathOrderCounter;

        public ServiceBuilder(Deployment.DeploymentBuilder parentBuilder, String name) {
            super(parentBuilder, name);
            applicationPaths = new HashMap<>();
            libraryPaths = new HashSet<>();
            environmentVariables = new HashMap<>();
            dockerImage = Constants.DEFAULT_BASE_DOCKER_IMAGE_NAME;
            pathOrderCounter = 0;
        }

        public ServiceBuilder(String name) {
            this(null, name);
        }

        public ServiceBuilder(Deployment.DeploymentBuilder parentBuilder, Service instance) {
            super(parentBuilder, instance);
            dockerImage = new String(instance.dockerImage);
            instrumentableAddress = new String(instance.instrumentableAddress);
            runCommand = new String(instance.runCommand);
            serviceType = instance.serviceType;
            applicationPaths = new HashMap<>(instance.applicationPaths);
            libraryPaths = new HashSet<>(instance.libraryPaths);
            environmentVariables = new HashMap<>(instance.environmentVariables);
            pathOrderCounter = instance.pathOrderCounter;
        }

        public ServiceBuilder(Service instance) {
            this(null, instance);
        }

        public ServiceBuilder dockerImage(String dockerImage) {
            this.dockerImage = dockerImage;
            return this;
        }

        public ServiceBuilder relativeInstrumentableAddress(String instrumentableAddress) {
            this.instrumentableAddress = instrumentableAddress;
            return this;
        }

        public ServiceBuilder runCommand(String runCommand) {
            this.runCommand = runCommand;
            return this;
        }

        public ServiceBuilder serviceType(ServiceType serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        public ServiceBuilder applicationPath(String path) {
            applicationPath(path, false, PathUtil.getLastFolderOrFileName(path));
            return this;
        }

        public ServiceBuilder applicationPath(String path, String targetPath) {
            applicationPath(path, false, targetPath);
            return this;
        }

        public ServiceBuilder applicationPath(String path, Boolean isLibrary) {
            applicationPath(path, isLibrary, PathUtil.getLastFolderOrFileName(path));
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

        public ServiceBuilder environmentVariable(String name, String value) {
            this.environmentVariables.put(name, value);
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
