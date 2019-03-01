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

package io.failify;

import io.failify.verification.DeploymentVerifier;
import io.failify.verification.InternalReferencesVerifier;
import io.failify.verification.RunSequenceVerifier;
import io.failify.verification.SchedulingOperationVerifier;
import io.failify.workspace.NodeWorkspace;
import io.failify.dsl.entities.Deployment;
import io.failify.exceptions.InstrumentationException;
import io.failify.exceptions.RuntimeEngineException;
import io.failify.exceptions.WorkspaceException;
import io.failify.execution.EventService;
import io.failify.execution.RuntimeEngine;
import io.failify.execution.LimitedRuntimeEngine;
import io.failify.instrumentation.InstrumentationEngine;
import io.failify.instrumentation.RunSequenceInstrumentationEngine;
import io.failify.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * This class is the main point of contact for all the test cases. Given a deployment definition this class deploys the
 * system into the desired runtime engine. Then, it is possible to enforce test case events through this class or manipulate
 * the deployed environment through calling runtime() method and getting access to the runtime engine
 */
public class FailifyRunner {
    private static final Logger logger = LoggerFactory.getLogger(FailifyRunner.class);
    private WorkspaceManager workspaceManager;
    private final Deployment deployment;
    private final List<DeploymentVerifier> verifiers;
    private RuntimeEngine runtimeEngine;
    private List<InstrumentationEngine> instrumentationEngines;

    /**
     * Constructor
     * @param deployment the deployment definition object
     */
    public FailifyRunner(Deployment deployment) {
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

    /**
     * Adds additional instrumentation engine to the runner. Note that run sequence instrumentation engine is included
     * by default
     * @param instrumentationEngine instance to be added
     */
    public void addInstrumentationEngine(InstrumentationEngine instrumentationEngine) {
        instrumentationEngines.add(instrumentationEngine);
    }

    /**
     * Creates a new runner instance and starts the deployment
     * @param deployment the deployment definition object
     * @return the created runner instance
     */
    public static FailifyRunner run(Deployment deployment) {
        logger.info("Starting FailifyRunner ...");
        FailifyRunner failifyRunner = new FailifyRunner(deployment);
        failifyRunner.start();
        return failifyRunner;
    }

    /**
     * This method waits indefinitely for the run sequence to be enforced completely, and then returns.
     * @throws TimeoutException if either type of timeout happens
     */
    public void waitForRunSequenceCompletion() throws TimeoutException {
        waitForRunSequenceCompletion(null,null,false);
    }

    /**
     * This method waits indefinitely for the run sequence to be enforced completely, stops the runner if required,
     * and then returns.
     * @param stopAfter the flag to require stopping the runner after run sequence completion
     * @throws TimeoutException if either type of timeout happens
     */
    public void waitForRunSequenceCompletion(boolean stopAfter) throws TimeoutException {
        waitForRunSequenceCompletion(null,null, stopAfter);
    }

    /**
     * This method waits for the run sequence to be enforced completely, and then returns. If timeout param is not null,
     * after waiting for the expected amount the method throws an exception.
     * @param timeout the waiting timeout in seconds
     * @throws TimeoutException if either type of timeout happens
     */
    public void waitForRunSequenceCompletion(Integer timeout) throws TimeoutException {
        waitForRunSequenceCompletion(timeout,null,false);
    }

    /**
     * This method waits for the run sequence to be enforced completely, stops the runner if required, and then returns.
     * If timeout param is not null, after waiting for the expected amount the method throws an exception.
     * @param timeout the waiting timeout in seconds
     * @param stopAfter the flag to require stopping the runner after run sequence completion
     * @throws TimeoutException if either type of timeout happens
     */
    public void waitForRunSequenceCompletion(Integer timeout, boolean stopAfter) throws TimeoutException {
        waitForRunSequenceCompletion(timeout,null, stopAfter);
    }

    /**
     * This method waits for the run sequence to be enforced completely, and then returns.
     * If desired it is possible to specify two different types of timeout for this method. If timeout param is not null,
     * after waiting for the expected amount the method throws an exception. If nextEventReceiptTimeout is not null, if
     * after the expected amount of time no new event is marked as satisfied in the event server, this method throws an
     * exception
     * @param timeout the waiting timeout in seconds
     * @param nextEventReceiptTimeout the number of seconds to wait until timeout the receipt of the next event in the
     *                                run sequence
     * @throws TimeoutException if either type of timeout happens
     */
    public void waitForRunSequenceCompletion(Integer timeout, Integer nextEventReceiptTimeout) throws TimeoutException {
        waitForRunSequenceCompletion(timeout,nextEventReceiptTimeout,false);
    }

    /**
     * This method waits for the run sequence to be enforced completely, stops the runner if required, and then returns.
     * If desired it is possible to specify two different types of timeout for this method. If timeout param is not null,
     * after waiting for the expected amount the method throws an exception. If nextEventReceiptTimeout is not null, if
     * after the expected amount of time no new event is marked as satisfied in the event server, this method throws an
     * exception
     * @param timeout the waiting timeout in seconds
     * @param nextEventReceiptTimeout the number of seconds to wait until timeout the receipt of the next event in the
     *                                run sequence
     * @param stopAfter the flag to require stopping the runner after run sequence completion
     * @throws TimeoutException if either type of timeout happens
     */
    public void waitForRunSequenceCompletion(Integer timeout, Integer nextEventReceiptTimeout, boolean stopAfter)
            throws TimeoutException {

        Integer originalTimeout = timeout;
        while (!isStopped() && (timeout == null || timeout > 0)) {

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

            if (timeout != null) {
                timeout--;
            }
        }

        if (timeout != null && timeout <= 0) {
            throw new TimeoutException("The Wait timeout for run sequence completion (" + originalTimeout + " seconds) is passed!");
        }
    }

    public Deployment getDeployment() {
        return deployment;
    }

    /**
     * @return an interface to manipulate the deployed environment in the test cases
     */
    public LimitedRuntimeEngine runtime() {
        return runtimeEngine;
    }

    private void start() {
        try {
            // Register the shutdown hook
            Runtime.getRuntime().addShutdownHook(new FailifyShutdownHook(this));

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

    /**
     * Stops the runner by killing all the deployed nodes
     */
    public void stop() {
        stop(true, 0);
    }

    /**
     * Stops the runner by stopping or killing all the deployed nodes
     * @param kill the flag to require killing of the nodes
     */
    public void stop(boolean kill) {
        stop(kill, Constants.DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_STOP);
    }

    /**
     * Stops the runner by stopping or killing all the deployed nodes
     * @param kill the flag to require killing of the nodes
     * @param secondsUntilForcedStop if stopping the nodes is desired, the runner will wait for this amount of time in
     *                               seconds and then forces the stop by killing the nodes
     */
    public void stop(boolean kill, Integer secondsUntilForcedStop) {
        logger.info("Stopping FailifyRunner ...");
        if (runtimeEngine != null) {
            runtimeEngine.stop(kill, secondsUntilForcedStop);
        }
    }

    /**
     * @return true is the runner is stopped or not started yet, otherwise false
     */
    public boolean isStopped() {
        if (runtimeEngine == null) {
            return true;
        }
        return runtimeEngine.isStopped();
    }
}

class FailifyShutdownHook extends Thread {
    private static Logger logger = LoggerFactory.getLogger(FailifyShutdownHook.class);

    FailifyRunner runner;

    public FailifyShutdownHook(FailifyRunner runner) {
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
