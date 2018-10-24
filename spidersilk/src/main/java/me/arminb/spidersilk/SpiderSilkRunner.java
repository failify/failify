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
import me.arminb.spidersilk.exceptions.InstrumentationException;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.exceptions.WorkspaceException;
import me.arminb.spidersilk.execution.EventService;
import me.arminb.spidersilk.execution.RuntimeEngine;
import me.arminb.spidersilk.execution.LimitedRuntimeEngine;
import me.arminb.spidersilk.execution.NextEventReceiptTimeoutCheckerThread;
import me.arminb.spidersilk.instrumentation.InstrumentationEngine;
import me.arminb.spidersilk.verification.*;
import me.arminb.spidersilk.workspace.NodeWorkspace;
import me.arminb.spidersilk.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SpiderSilkRunner {
    private static final Logger logger = LoggerFactory.getLogger(SpiderSilkRunner.class);
    private WorkspaceManager workspaceManager;
    private final Deployment deployment;
    private final List<DeploymentVerifier> verifiers;
    private RuntimeEngine runtimeEngine;
    private InstrumentationEngine instrumentationEngine;

    private SpiderSilkRunner(Deployment deployment, RuntimeEngine runtimeEngine) {
        this.deployment = deployment;
        this.runtimeEngine = runtimeEngine;
        this.workspaceManager = new WorkspaceManager(deployment);

        // Verifiers
        verifiers = Collections.unmodifiableList(Arrays.asList(
                new InternalReferencesVerifier(deployment),
                new RunSequenceVerifier(deployment),
                new SchedulingOperationVerifier(deployment)
        ));
    }

    public static SpiderSilkRunner run(Deployment deployment, RuntimeEngine runtimeEngine) {
        logger.info("Starting SpiderSilkRunner ...");
        SpiderSilkRunner spiderSilkRunner = new SpiderSilkRunner(deployment, runtimeEngine);
        spiderSilkRunner.start();

        // Starts last event receipt timeout checker
        if (deployment.getNextEventReceiptTimeout() != null) {
            logger.info("Starting next event receipt timeout checker thread with {} seconds timeout ..."
                    , deployment.getNextEventReceiptTimeout());
            new NextEventReceiptTimeoutCheckerThread(spiderSilkRunner).start();
        }

        return spiderSilkRunner;
    }

    public void waitForRunSequenceCompletion() {
        waitForRunSequenceCompletion(false);
    }

    public void waitForRunSequenceCompletion(boolean stopAfter) {
        while (!isStopped()) {
            if (EventService.getInstance().isTheRunSequenceCompleted()) {
                logger.info("The run sequence is completed!");
                logger.info("Waiting for {} seconds before stopping the runner ...",
                        deployment.getSecondsToWaitForCompletion());
                try {
                    Thread.sleep(deployment.getSecondsToWaitForCompletion() * 1000);
                } catch (InterruptedException e) {
                    logger.error("The SpiderSilkRunner wait for completion thread sleep has been interrupted!", e);
                }
                if (stopAfter && !isStopped()) {
                    stop();
                }
            }
        }
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public LimitedRuntimeEngine runtime() {
        return runtimeEngine;
    }

    private void start() {
        try {
            // Register the shutdown hook
            Runtime.getRuntime().addShutdownHook(new SpiderSilkShutdownHook(this));

            // Verify the deployment definition
            logger.info("Verifying the deployment definition ...");
            for (DeploymentVerifier verifier : verifiers) {
                verifier.verify();
            }

            // Setup the nodes' workspaces
            logger.info("Creating the nodes' workspaces ...");
            Map<String, NodeWorkspace> nodeWorkspaceMap = workspaceManager.createWorkspace(deployment);

            // Starting the Event Server to get the port number for instrumentation
            logger.info("Starting event server ...");
            runtimeEngine.startEventServer();
            logger.info("Event server is started at port " + runtimeEngine.getEventServerPortNumber());

            // Instrument the nodes binaries. This shouldn't change any of the application paths
            logger.info("Starting the instrumentation process ...");
            instrumentationEngine = new InstrumentationEngine(deployment, nodeWorkspaceMap);
            instrumentationEngine.instrumentNodes(runtimeEngine.getEventServerPortNumber());
            logger.info("Instrumentation process is completed!");

            // Starting the runtime engine
            logger.info("Starting the runtime engine ...");

            runtimeEngine.setNodeWorkspaceMap(nodeWorkspaceMap);
            runtimeEngine.start(this);
        } catch (RuntimeEngineException e) {
            if (!isStopped()) {
                stop();
            }
            throw new RuntimeException(e);
        } catch (InstrumentationException e) {
            throw new RuntimeException(e);
        } catch (WorkspaceException e) {
            throw new RuntimeException(e);
        } catch (Throwable e) {
            logger.error("An unexpected error has happened. Stopping ...");
            if (!isStopped()) {
                stop();
            }
            throw e;
        }
    }

    public void stop() {
        logger.info("Stopping SpiderSilkRunner ...");
        runtimeEngine.stop();
    }

    public boolean isStopped() {
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
