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

import me.arminb.spidersilk.dsl.DeploymentEntity;

/**
 * An abstraction for a service or application inside a distributed system.
 */
public class Service extends DeploymentEntity {
    private final String applicationAddress;
    private final String runCommand;
    private final String libDir;
    private final ServiceType serviceType;

    private Service(ServiceBuilder builder) {
        super(builder.getName());
        applicationAddress = builder.applicationAddress;
        runCommand = builder.runCommand;
        libDir = builder.libDir;
        serviceType = builder.serviceType;
    }

    public String getApplicationAddress() {
        return applicationAddress;
    }

    public String getRunCommand() {
        return runCommand;
    }

    public String getLibDir() {
        return libDir;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }
    
    public static class ServiceBuilder extends DeploymentBuilderBase<Service, Deployment.DeploymentBuilder> {
        private String applicationAddress;
        private String runCommand;
        private String libDir;
        private ServiceType serviceType;

        public ServiceBuilder(Deployment.DeploymentBuilder parentBuilder, String name) {
            super(parentBuilder, name);
        }

        public ServiceBuilder(String name) {
            super(name);
        }

        public ServiceBuilder(Service instance) {
            super(instance);
            applicationAddress = instance.applicationAddress;
            runCommand = instance.runCommand;
            libDir = instance.runCommand;
            serviceType = instance.serviceType;
        }

        public ServiceBuilder applicationAddress(String applicationAddress) {
            this.applicationAddress = applicationAddress;
            return this;
        }

        public ServiceBuilder runCommand(String runCommand) {
            this.runCommand = runCommand;
            return this;
        }

        public ServiceBuilder libDir(String libDir) {
            this.libDir = libDir;
            return this;
        }

        public ServiceBuilder serviceType(ServiceType serviceType) {
            this.serviceType = serviceType;
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
