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

package me.arminb.spidersilk.deployment;

import java.util.HashMap;
import java.util.Map;

public class Service extends DeploymentBase {
    private final String jarFile;
    private final String runCommand;
    private final Map<String, Event> events;

    private Service(ServiceBuilder builder) {
        super(builder.name);
        jarFile = builder.jarFile;
        runCommand = builder.runCommand;
        events = builder.events;
    }

    public String getJarFile() {
        return jarFile;
    }

    public String getRunCommand() {
        return runCommand;
    }

    public Event getEvent(String name) {
        return events.get(name);
    }

    public static class Event extends DeploymentBase {
        private final String event;

        private Event(String name, String event) {
            super(name);
            this.event = event;
        }

        public String getEvent() {
            return event;
        }
    }

    public static class ServiceBuilder extends DeploymentBuilderBase<Service, Deployment.DeploymentBuilder> {
        private String jarFile;
        private String runCommand;
        private Map<String, Event> events;

        public ServiceBuilder(Deployment.DeploymentBuilder parentBuilder, String name) {
            super(parentBuilder, name);
            events = new HashMap<>();
        }

        public ServiceBuilder withEvent(String name, String event) {
            events.put(name, new Event(name, event));
            return this;
        }

        public ServiceBuilder event(Event event) {
            events.put(event.getName(), event);
            return this;
        }

        public ServiceBuilder jarFile(String jarFile) {
            this.jarFile = jarFile;
            return this;
        }

        public ServiceBuilder runCommand(String runCommand) {
            this.runCommand = runCommand;
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
