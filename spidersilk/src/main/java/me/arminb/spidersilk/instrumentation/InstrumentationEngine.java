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

import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.entities.Service;
import me.arminb.spidersilk.dsl.entities.ServiceType;
import me.arminb.spidersilk.dsl.events.InternalEvent;
import me.arminb.spidersilk.exceptions.InstrumentationException;
import me.arminb.spidersilk.instrumentation.java.JavaInstrumentor;
import me.arminb.spidersilk.util.HostUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class InstrumentationEngine {
    private final static Logger logger = LoggerFactory.getLogger(InstrumentationEngine.class);
    private final Deployment deployment;
    private final Path workingDirectory;

    public InstrumentationEngine(Deployment deployment, Path workingDirectory) {
        this.deployment = deployment;
        this.workingDirectory = workingDirectory;
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

        for (Node node: deployment.getNodes().values()) {
            retMap.put(
                    node.getName(),
                    FilenameUtils.normalize(Paths.get(deployment.getService(node.getServiceName())
                            .getApplicationAddress()).toFile().getAbsolutePath().toString())
            );
        }

        String[] eventNames = deployment.getRunSequence().split("\\W+");
        Map<Node, List<InternalEvent>> nodeMap = new HashMap<>();

        // Categorizes internal events that are referred in run sequence based on their corresponding node
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

        prepareWorkspaceForNodes(nodeMap.keySet());

        // Instruments each node's binaries based on its service type
        for (Node node: nodeMap.keySet()) {
            Service service = deployment.getService(node.getServiceName());
            String newApplicationAddress = service.getApplicationAddress();
            List<InstrumentationDefinition> instrumentationDefinitions = new ArrayList<>();

            for (InternalEvent event: nodeMap.get(node)) {
                instrumentationDefinitions.addAll(event.generateInstrumentationDefinitions(deployment));
            }

            // Only does instrumentation when there is an instrumentation definition
            if (!instrumentationDefinitions.isEmpty()) {
                // Preprocesses and orders instrumentation definitions list
                instrumentationDefinitions = preProcessInstrumentationDefinitions(instrumentationDefinitions);

                // Performs the actual instrumentation and receives the new instrumented file name
                Instrumentor instrumentor = getInstrumentor(service.getServiceType());
                newApplicationAddress = instrumentor.instrument(new NodeWorkspace(
                        FilenameUtils.normalize(workingDirectory.resolve(node.getName()).resolve(new File(service.getApplicationAddress()).getName()).toFile().getAbsolutePath()),
                        service.getLibDir(),
                        instrumentationDefinitions
                ));
            }
            retMap.put(node.getName(), newApplicationAddress);
        }

        return retMap;
    }

    private List<InstrumentationDefinition> preProcessInstrumentationDefinitions(List<InstrumentationDefinition> definitions) throws InstrumentationException {
        InstrumentationDefinition.InstrumentationDefinitionBuilder mainInstrumentation;

        // Adds configure operation to the main instrumentation definition
        try {
            mainInstrumentation = InstrumentationDefinition.builder()
                .instrumentationPoint(Constants.INSTRUMENTATION_POINT_MAIN, InstrumentationPoint.Position.BEFORE)
                .withInstrumentationOperation(SpiderSilkRuntimeOperation.CONFIGURE)
                    .parameter(HostUtil.getLocalIpAddress())
                    .parameter(deployment.getEventServerPortNumber().toString()).and();
        } catch (UnknownHostException e) {
            throw new InstrumentationException("The IP address of the system cannot be determined!");
        }

        // Unifies instrumentation definitions for MAIN with configure operation at first
        Iterator<InstrumentationDefinition> definitionIterator = definitions.iterator();
        while (definitionIterator.hasNext()){
            InstrumentationDefinition definition = definitionIterator.next();
            if (definition.getInstrumentationPoint().getMethodName().equals(Constants.INSTRUMENTATION_POINT_MAIN)) {
                for (InstrumentationOperation operation: definition.getInstrumentationOperations()) {
                    mainInstrumentation.instrumentationOperation(operation);
                }
                definitionIterator.remove();
            }
        }
        definitions.add(mainInstrumentation.build());

        // Unifies instrumentation definitions for each instrumentation point
        Map<InstrumentationPoint, List<InstrumentationOperation>> instrumentationPointMap = new HashMap<>();
        for (InstrumentationDefinition definition: definitions) {
            if (!instrumentationPointMap.containsKey(definition.getInstrumentationPoint())) {
                instrumentationPointMap.put(definition.getInstrumentationPoint(), new ArrayList<>());
            }
            for (InstrumentationOperation operation: definition.getInstrumentationOperations()) {
                instrumentationPointMap.get(definition.getInstrumentationPoint()).add(operation);
            }
        }

        List<InstrumentationDefinition> retList = new ArrayList<>();

        for (InstrumentationPoint instrumentationPoint: instrumentationPointMap.keySet()) {
            InstrumentationDefinition.InstrumentationDefinitionBuilder builder = InstrumentationDefinition.builder();
            builder.instrumentationPoint(instrumentationPoint.getMethodName(), instrumentationPoint.getPosition());
            for (InstrumentationOperation operation: instrumentationPointMap.get(instrumentationPoint)) {
                builder.instrumentationOperation(operation);
            }
            retList.add(builder.build());
        }

        return retList;
    }

    private void prepareWorkspaceForNodes(Set<Node> nodes) throws InstrumentationException {
        // Create node directories
        for (Node node: nodes) {
            Path nodePath = workingDirectory.resolve(node.getName());
            try {
                Files.createDirectory(nodePath);
            } catch (IOException e) {
                throw new InstrumentationException("Error in creating SpiderSilk node directory \"" + node.getName() + "\"!");
            }
        }

        // Copy over contents in the application address of the nodes to the corresponding folder
        for (Node node: nodes) {
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
