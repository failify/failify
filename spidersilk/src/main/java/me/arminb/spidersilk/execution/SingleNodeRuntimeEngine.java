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

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SingleNodeRuntimeEngine extends RuntimeEngine {
    Map<String, Process> processMap;

    public SingleNodeRuntimeEngine(Deployment deployment, Map<String, String> instrumentedApplicationAddressMap, Path workingDirectory) {
        super(deployment, instrumentedApplicationAddressMap, workingDirectory);
        processMap = new HashMap<>();
    }

    @Override
    protected void startNodes() throws RuntimeEngineException {
        for (Node node: deployment.getNodes().values()) {
            Service service = deployment.getService(node.getServiceName());
            ProcessBuilder processBuilder = new ProcessBuilder();
            Map<String, String> environment = processBuilder.environment();
            String runCommand = service.getRunCommand();
            if (node.getRunCommand() != null) {
                runCommand = node.getRunCommand();
            }
            environment.put(Constants.APPLICATION_ADDRESS_ENVVAR_NAME, instrumentedApplicationAddressMap.get(node.getName()));
            // TODO this should change to storing in file
            processBuilder.inheritIO();
            String currentShell = ShellUtil.getCurrentShellAddress();
            if (currentShell == null) {
                throw new RuntimeEngineException("Cannot find the current system shell to start the nodes processes!");
            }
            processBuilder.command(currentShell, "-c", runCommand);
            try {
                processMap.put(node.getName(), processBuilder.start());
            } catch (IOException e) {
                throw new RuntimeEngineException(e.getMessage());
            }
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

}
