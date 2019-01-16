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
import me.arminb.spidersilk.instrumentation.InstrumentationEngine;
import me.arminb.spidersilk.instrumentation.RunSequenceInstrumentationEngine;
import me.arminb.spidersilk.verification.*;
import me.arminb.spidersilk.workspace.NodeWorkspace;
import me.arminb.spidersilk.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeoutException;

public class SpiderSilkRunner {
    private static final Logger logger = LoggerFactory.getLogger(SpiderSilkRunner.class);
    private WorkspaceManager workspaceManager;
    private final Deployment deployment;
    private final List<DeploymentVerifier> verifiers;
    private RuntimeEngine runtimeEngine;
    private List<InstrumentationEngine> instrumentationEngines;

    public SpiderSilkRunner(Deployment deployment) {
        this.deployment = deployment;
        this.instrumentationEngines = new ArrayList<>();
        this.workspaceManager = new WorkspaceManager(deployment);

        // Verifiers
        verifiers = Collections.unmodifiableList(Arrays.asList(
                new InternalReferencesVerifier(deployment),
                new RunSequenceVerifier(deployment),
                new SchedulingOperationVerifier(deployment)
        ));

        // Add the default instrumentation engine
        instrumentationEngines.add(new RunSequenceInstrumentationEngine());
    }

    public void addInstrumentationEngine(InstrumentationEngine instrumentationEngine) {
        instrumentationEngines.add(instrumentationEngine);
    }

    public static SpiderSilkRunner run(Deployment deployment) {
        logger.info("Starting SpiderSilkRunner ...");
        SpiderSilkRunner spiderSilkRunner = new SpiderSilkRunner(deployment);
        spiderSilkRunner.start();
        return spiderSilkRunner;
    }

    public void waitForRunSequenceCompletion(Integer timeout) throws TimeoutException {
        waitForRunSequenceCompletion(timeout,null,false);
    }

    public void waitForRunSequenceCompletion(Integer timeout, boolean stopAfter) throws TimeoutException {
        waitForRunSequenceCompletion(timeout,null,stopAfter);
    }

    public void waitForRunSequenceCompletion(Integer timeout, Integer nextEventReceiptTimeout) throws TimeoutException {
        waitForRunSequenceCompletion(timeout,nextEventReceiptTimeout,false);
    }

    public void waitForRunSequenceCompletion(Integer timeout, Integer nextEventReceiptTimeout, boolean stopAfter)
            throws TimeoutException {

        int originalTimeout = timeout;
        while (!isStopped() && timeout > 0) {
            if (EventService.getInstance().isTheRunSequenceCompleted()) {
                logger.info("The run sequence is completed!");

                if (stopAfter && !isStopped()) {
                    stop();
                }

                return;
            }

            if (deployment.getRunSequence() != null && !deployment.getRunSequence().isEmpty() &&
                    EventService.getInstance().isLastEventReceivedTimeoutPassed(nextEventReceiptTimeout)) {
                throw new TimeoutException("The timeout for receiving the next event (" + nextEventReceiptTimeout + " seconds) is passed!");
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // TODO is this the best thing to do ?
                logger.warn("The run sequence completion wait sleep thread is interrupted");
            }
            timeout--;
        }

        if (timeout <= 0) {
            throw new TimeoutException("The Wait timeout for run sequence completion (" + originalTimeout + " seconds) is passed!");
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

            // Instrument the nodes binaries. This shouldn't change any of the application paths
            logger.info("Starting the instrumentation process ...");
            for (InstrumentationEngine instrumentationEngine: instrumentationEngines) {
                logger.info("Instrumenting using {}", instrumentationEngine.getClass().getName());
                instrumentationEngine.instrumentNodes(deployment, nodeWorkspaceMap);
            }
            logger.info("Instrumentation process is completed!");

            // Starting the runtime engine
            logger.info("Starting the runtime engine ...");

            runtimeEngine = RuntimeEngine.getRuntimeEngine(deployment, nodeWorkspaceMap);
            runtimeEngine.start(this);
        } catch (RuntimeEngineException e) {
            logger.error("An error happened while starting the runtime engine. Stopping ...", e);
            if (!isStopped()) {
                stop();
            }
            throw new RuntimeException(e);
        } catch (WorkspaceException | InstrumentationException e) {
            logger.error("An error happened while instrumenting the nodes", e);
            throw new RuntimeException(e);
        } catch (Throwable e) {
            logger.error("An unexpected error has happened. Stopping ...", e);
            if (!isStopped()) {
                stop();
            }
            throw e;
        }
    }

    public void stop() {
        stop(true, 0);
    }

    public void stop(boolean kill) {
        stop(kill, Constants.DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_STOP);
    }

    public void stop(boolean kill, Integer secondsUntilForcedStop) {
        logger.info("Stopping SpiderSilkRunner ...");
        runtimeEngine.stop(kill, secondsUntilForcedStop);
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
