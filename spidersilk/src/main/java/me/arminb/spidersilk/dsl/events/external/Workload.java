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

package me.arminb.spidersilk.dsl.events.external;

import me.arminb.spidersilk.dsl.events.ExternalEvent;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.exceptions.ExternalEventExecutionException;
import me.arminb.spidersilk.execution.RuntimeEngine;
import me.arminb.spidersilk.util.ShellUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * An abstraction for input workloads that should be fed into a distributed system. For the sake of being general, this is a
 * run command. This can be either a shell command or an sh file.
 */
public class Workload extends ExternalEvent {
    private final static Logger logger = LoggerFactory.getLogger(Workload.class);
    private final String runCommand;

    private Workload(WorkloadBuilder builder) {
        super(builder.getName());
        runCommand = builder.runCommand;
    }

    public String getRunCommand() {
        return runCommand;
    }

    @Override
    protected void execute(RuntimeEngine runtimeEngine) throws ExternalEventExecutionException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.inheritIO();
        String currentShell = ShellUtil.getCurrentShellAddress();
        if (currentShell == null) {
            throw new ExternalEventExecutionException("Cannot find the current system shell to start the workload process!");
        }
        processBuilder.command(currentShell, "-c", runCommand);
        Process runningProcess;

        logger.info("Starting workload {} ...", name);
        try {
            runningProcess = processBuilder.start();
        } catch (IOException e) {
            throw new ExternalEventExecutionException(e.getMessage());
        }
        try {
            runningProcess.waitFor();
        } catch (InterruptedException e) {
            throw new ExternalEventExecutionException("The workload " + name + " process has been interrupted!");
        }
        // Wait a little bit for the process output to appear in the logs
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.info("Workload {} is completed!", name);
    }

    public static class WorkloadBuilder extends DeploymentBuilderBase<Workload, Deployment.DeploymentBuilder> {
        private String runCommand;

        public WorkloadBuilder(Deployment.DeploymentBuilder parentBuilder, String name) {
            super(parentBuilder, name);
        }

        public WorkloadBuilder(String name) {
            this(null, name);
        }

        public WorkloadBuilder(Deployment.DeploymentBuilder parentBuilder, Workload instance) {
            super(parentBuilder, instance);
            runCommand = new String(instance.runCommand);
        }

        public WorkloadBuilder(Workload instance) {
            this(null, instance);
        }

        public WorkloadBuilder runCommand(String runCommand) {
            this.runCommand = runCommand;
            return this;
        }

        @Override
        public Workload build() {
            return new Workload(this);
        }

        @Override
        protected void returnToParent(Workload builtObj) {
            parentBuilder.workload(builtObj);
        }
    }
}
