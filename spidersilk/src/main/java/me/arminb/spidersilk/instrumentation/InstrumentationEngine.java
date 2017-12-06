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

package me.arminb.spidersilk.instrumentation;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.entities.Service;
import me.arminb.spidersilk.dsl.entities.ServiceType;
import me.arminb.spidersilk.dsl.events.InternalEvent;
import me.arminb.spidersilk.exceptions.InstrumentationException;
import me.arminb.spidersilk.instrumentation.java.JavaInstrumentor;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstrumentationEngine {
    private final static Logger logger = LoggerFactory.getLogger(InstrumentationEngine.class);
    private final Deployment deployment;
    private final Path workingDirectory;

    public InstrumentationEngine(Deployment deployment) {
        this.deployment = deployment;
        this.workingDirectory = Paths.get("./.SpiderSilkWorkingDirectory");
    }

    private Instrumentor getInstrumentor(ServiceType serviceType) {
        if (serviceType == ServiceType.JAVA) {
            return new JavaInstrumentor();
        } else {
            return null;
        }
    }

    public Map<String, String> instrumentNodes() throws InstrumentationException {
        Map<String, String> retMap = new HashMap<>();
        prepareWorkspaceForNodes();
        String[] eventNames = deployment.getRunSequence().split("\\S+");
        Map<Node, List<InternalEvent>> nodeMap = new HashMap<>();

        // Categorize internal events that are referred in run sequence based on their corresponding node
        for (String eventName: eventNames) {
            if (deployment.getReferableDeploymentEntity(eventName) instanceof InternalEvent) {
                InternalEvent event = (InternalEvent) deployment.getReferableDeploymentEntity(eventName);
                Node node = deployment.getNode(event.getNodeName());
                if (nodeMap.containsKey(node)) {
                    nodeMap.get(node).add(event);
                } else {
                    List<InternalEvent> eventList = new ArrayList<>();
                    eventList.add(event);
                    nodeMap.put(node,eventList);
                }
            }
        }

        // Instrument each node's binaries based on its service type
        for (Node node: nodeMap.keySet()) {
            Service service = deployment.getService(node.getServiceName());
            List<InstrumentationDefinition> instrumentationDefinitions = new ArrayList<>();
            for (InternalEvent event: nodeMap.get(node)) {
                instrumentationDefinitions.addAll(event.generateInstrumentationDefinitions(deployment));
            }

            Instrumentor instrumentor = getInstrumentor(service.getServiceType());
            String newApplicationAddress = instrumentor.instrument(new NodeWorkspace(
                    workingDirectory.resolve(node.getName()).resolve(new File(service.getApplicationAddress()).getName()).toFile().getAbsolutePath(),
                    service.getLibDir(),
                    instrumentationDefinitions
            ));
            retMap.put(node.getName(), newApplicationAddress);
        }

        return retMap;
    }

    public void cleanUp() {

    }

    private void prepareWorkspaceForNodes() throws InstrumentationException {
        // Create workspace directory
        try {
            Files.deleteIfExists(workingDirectory);
            Files.createDirectory(workingDirectory);
        } catch (IOException e) {
            throw new InstrumentationException("Error in creating SpiderSilk working directory!");
        }

        // Create node directories
        for (Node node: deployment.getNodes().values()) {
            Path nodePath = workingDirectory.resolve(node.getName());
            try {
                Files.createDirectory(nodePath);
            } catch (IOException e) {
                throw new InstrumentationException("Error in creating SpiderSilk node directory \"" + node.getName() + "\"!");
            }
        }

        // Copy over contents in the application address of the nodes to the corresponding folder
        for (Node node: deployment.getNodes().values()) {
            Path nodePath = workingDirectory.resolve(node.getName());
            try {
                File applicationAddress = Paths.get(deployment.getService(node.getServiceName()).getApplicationAddress()).toFile();
                if (applicationAddress.isFile()) {
                    FileUtils.copyFileToDirectory(applicationAddress, nodePath.toFile());
                } else {
                    FileUtils.copyDirectoryToDirectory(applicationAddress, nodePath.toFile());
                }
            } catch (IOException e) {
                throw new InstrumentationException("Error in copying over node " + node.getName() + " binaries to its workspace!");
            }
        }
    }


}
