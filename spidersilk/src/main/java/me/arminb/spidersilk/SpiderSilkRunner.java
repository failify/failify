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

package me.arminb.spidersilk;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.events.ExternalEvent;
import me.arminb.spidersilk.exceptions.InstrumentationException;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.execution.EventService;
import me.arminb.spidersilk.execution.RunMode;
import me.arminb.spidersilk.execution.RuntimeEngine;
import me.arminb.spidersilk.instrumentation.InstrumentationEngine;
import me.arminb.spidersilk.verification.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SpiderSilkRunner {
    private static final Logger logger = LoggerFactory.getLogger(SpiderSilkRunner.class);
    private Path workingDirectory;
    private final Deployment deployment;
    private final RunMode runMode;
    private final List<DeploymentVerifier> verifiers;
    private RuntimeEngine runtimeEngine;
    private InstrumentationEngine instrumentationEngine;

    private SpiderSilkRunner(Deployment deployment, RunMode runMode) {
        this.deployment = deployment;
        this.runMode = runMode;

        // Verifiers
        verifiers = Collections.unmodifiableList(Arrays.asList(
                new RunSequenceVerifier(deployment),
                new SchedulingOperationVerifier(deployment),
                new InternalReferencesVerifier(deployment),
                new StackTraceVerifier(deployment)
        ));
    }

    public static SpiderSilkRunner run(Deployment deployment, RunMode runMode) {
        logger.info("Starting SpiderSilkRunner ...");
        SpiderSilkRunner spiderSilkRunner = new SpiderSilkRunner(deployment, runMode);
        spiderSilkRunner.start();
        while (true) {
            if (EventService.getInstance().isTheRunSequenceCompleted()) {
                break;
            }
        }
        logger.info("The run sequence is completed!");
        logger.info("Waiting for {} seconds before stopping the runner ...", deployment.getSecondsToWaitForCompletion());
        try {
            Thread.sleep(deployment.getSecondsToWaitForCompletion() * 1000);
        } catch (InterruptedException e) {
            logger.warn("The SpiderSilkRunner wait for completion thread sleep has been interrupted!", e);
        }
        logger.info("Stopping SpiderSilkRunner ...");
        spiderSilkRunner.stop();
        return spiderSilkRunner;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    private void start() {
        // Register the shutdown hook
        Runtime.getRuntime().addShutdownHook(new SpiderSilkShutdownHook(this));
        // Verify the deployment definition
        logger.info("Verifying the deployment definition ...");
        for (DeploymentVerifier verifier: verifiers) {
            verifier.verify();
        }

        // Create workspace directory
        try {
            workingDirectory = Paths.get(FilenameUtils.normalize(Paths.get(".", ".SpiderSilkWorkingDirectory").toAbsolutePath().toString()));
            cleanUp();
            Files.createDirectory(workingDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Error in creating SpiderSilk working directory!");
        }

        // Instrument the nodes binaries and get the new application addresses map
        logger.info("Starting the instrumentation process ...");
        instrumentationEngine = new InstrumentationEngine(deployment, workingDirectory);
        Map<String, String> instrumentedApplicationAddressMap = null;
        try {
            instrumentedApplicationAddressMap = instrumentationEngine.instrumentNodes();
        } catch (InstrumentationException e) {
            throw new RuntimeException(e.getMessage());
        }
        logger.info("Instrumentation process is completed!");

        // Instantiate the proper runtime engine based on the run mode and start it
        logger.info("Starting the runtime engine ...");
        runtimeEngine = RuntimeEngine.getInstance(deployment, instrumentedApplicationAddressMap , runMode, workingDirectory);
        try {
            runtimeEngine.start();
        } catch (RuntimeEngineException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void cleanUp() {
        logger.info("Cleaning up the working directory at {}", workingDirectory.toAbsolutePath().toString());
        try {
            FileUtils.deleteDirectory(workingDirectory.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Error in cleaning up SpiderSilk working directory!", e);
        }
    }

    protected void stop() {
        logger.info("Stopping the runtime engine ...");
        if (runtimeEngine != null) {
            runtimeEngine.stop();
        }
    }

    protected boolean isStopped() {
        if (runtimeEngine == null) {
            return false;
        }
        return runtimeEngine.isStopped();
    }
}

class SpiderSilkShutdownHook extends Thread {
    private static Logger logger = LoggerFactory.getLogger(SpiderSilkShutdownHook.class);

    SpiderSilkRunner runner;

    public SpiderSilkShutdownHook(SpiderSilkRunner runner) {
        this.runner = runner;
    }

    @Override
    public void run() {
        if (!runner.isStopped()) {
            logger.info("Shutdown signal received!");
            runner.stop();
        }
    }
}
