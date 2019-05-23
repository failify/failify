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

import io.failify.dsl.entities.Node;
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
import io.failify.instrumentation.runseq.RunSequenceInstrumentationEngine;
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
            Map<String, NodeWorkspace> nodeWorkspaceMap = workspaceManager.createWorkspace();

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
        } catch (InstrumentationException e) {
            logger.error("An error happened while instrumenting the nodes", e);
            throw new RuntimeException(e);
        } catch (WorkspaceException e) {
            logger.error("An error happened while creating workspace for the nodes", e);
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
        logger.info("Cleaning up the working directory ...");
        workspaceManager.cleanUp();
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

    public void addNode(Node.LimitedBuilder limitedBuilder) throws WorkspaceException, RuntimeEngineException {
        Node node = limitedBuilder.build();
        NodeWorkspace nodeWorkspace = workspaceManager.createNodeWorkspace(node);
        runtimeEngine.addNewNode(node, nodeWorkspace);
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
