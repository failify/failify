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

import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.entities.Service;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.util.ShellUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SingleNodeRuntimeEngine extends RuntimeEngine {
    private static Logger logger = LoggerFactory.getLogger(SingleNodeRuntimeEngine.class);

    Map<String, Process> processMap;

    public SingleNodeRuntimeEngine(Deployment deployment) {
        super(deployment);
        processMap = new HashMap<>();
    }

    @Override
    protected void startNodes() throws RuntimeEngineException {
        for (Node node: deployment.getNodes().values()) {
            if (node.getOffOnStartup()) {
                logger.info("Skipping node " + node.getName() + " on startup since it is off!");
                continue;
            }

            Service service = deployment.getService(node.getServiceName());

            String runCommand = service.getRunCommand();
            if (node.getRunCommand() != null) {
                runCommand = node.getRunCommand();
            }

            ProcessBuilder processBuilder = new ProcessBuilder();
            getNodeEnvironmentVariablesMap(node.getName(), processBuilder.environment());
            // TODO this should change to storing in file
            processBuilder.inheritIO();
            // TODO this is not cross-platform
            String currentShell = ShellUtil.getCurrentShellAddress();
            if (currentShell == null) {
                throw new RuntimeEngineException("Cannot find the current system shell to start the nodes processes!");
            }

            logger.info("Starting node " + node.getName() + " ...");

            processBuilder.command(currentShell, "-c", runCommand);
            try {
                processMap.put(node.getName(), processBuilder.start());
            } catch (IOException e) {
                throw new RuntimeEngineException(e.getMessage());
            }

            logger.info("Node " + node.getName() + " is started!");
        }
    }

    @Override
    protected void stopNodes() {
        for (Process process: processMap.values()) {
            process.destroy();
        }
    }

    @Override
    public void stopNode(String nodeName, boolean kill) {
        Process process = processMap.get(nodeName);
        if (process != null) {
            if (kill) {
                process.destroyForcibly();
            } else {
                process.destroy();
            }
        }
    }

    @Override
    public void startNode(String nodeName) {

    }

    @Override
    public void restartNode(String nodeName) {

    }

    @Override
    public void clockDrift(String nodeName) {

    }

    @Override
    public void networkPartition(String nodeNames) {

    }

}
