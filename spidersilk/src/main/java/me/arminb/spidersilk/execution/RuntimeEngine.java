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

package me.arminb.spidersilk.execution;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.events.ExternalEvent;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.rt.SpiderSilk;
import me.arminb.spidersilk.util.HostUtil;

import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public abstract class RuntimeEngine {
    private EventServer eventServer;
    protected final Deployment deployment;
    protected final Map<String, String> instrumentedApplicationAddressMap;
    protected Path workingDirectory;
    protected boolean stopped;

    private static Map<RunMode, Class<? extends RuntimeEngine>> runModeMap = new HashMap<>();

    public static RuntimeEngine getInstance(Deployment deployment, Map<String, String> instrumentedApplicationAddressMap, RunMode runMode, Path workingDirectory) {
        if (runMode == RunMode.SINGLE_NODE) {
            return new SingleNodeRuntimeEngine(deployment, instrumentedApplicationAddressMap, workingDirectory);
        } else {
            return null;
        }
    }

    public RuntimeEngine(Deployment deployment, Map<String, String> instrumentedApplicationAddressMap, Path workingDirectory) {
        this.deployment = deployment;
        this.instrumentedApplicationAddressMap = instrumentedApplicationAddressMap;
        EventService.initialize(deployment);
        eventServer = new EventServer(deployment);
        this.workingDirectory = workingDirectory;
        this.stopped = false;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void start() throws RuntimeEngineException {
        // Configure local SpiderSilk runtime
        try {
            SpiderSilk.configure(HostUtil.getLocalIpAddress(), deployment.getEventServerPortNumber().toString());
        } catch (UnknownHostException e) {
            throw new RuntimeEngineException("Cannot get the local IP address to configure the local SpiderSilk runtime!");
        }

        startEventServer();
        startExternalEvents();

        try {
            startNodes();
        } catch (RuntimeEngineException e) {
            stop();
            throw e;
        }

        stopped = false;
    }

    protected void startExternalEvents() {
        // TODO only start those events that are present in the run sequence
        for (ExternalEvent externalEvent: deployment.getExecutableEntities().values()) {
            externalEvent.start(this);
        }
    }

    protected void startEventServer() throws RuntimeEngineException {
        eventServer.start();
    }



    public void stop() {
        stopExternalEvents();
        stopNodes();
        stopEventServer();
        stopped = true;
    }

    protected void stopExternalEvents() {
        for (ExternalEvent externalEvent: deployment.getExecutableEntities().values()) {
            externalEvent.stop();
        }
    }

    protected void stopEventServer() {
        eventServer.stop();
    }

    /**
     * This method should start all of the nodes. In case of a problem in startup of a node, all of the started nodes should be
     * stopped and a RuntimeEngine Exception should be thrown
     * @throws RuntimeEngineException
     */
    protected abstract void startNodes() throws RuntimeEngineException;
    protected abstract void stopNodes();
    public abstract void stopNode(String nodeName, boolean kill);

    public Path getWorkingDirectory() {
        return workingDirectory;
    }
}
