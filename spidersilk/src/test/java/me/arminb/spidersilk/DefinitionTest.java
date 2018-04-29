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
import me.arminb.spidersilk.dsl.entities.ServiceType;
import me.arminb.spidersilk.dsl.events.external.NetworkOperation;
import me.arminb.spidersilk.dsl.events.external.NodeOperation;
import me.arminb.spidersilk.dsl.events.internal.SchedulingOperation;
import me.arminb.spidersilk.exceptions.DeploymentVerificationException;
import me.arminb.spidersilk.execution.single_node.SingleNodeRuntimeEngine;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefinitionTest {
    public static final Logger logger = LoggerFactory.getLogger(DefinitionTest.class);

    @Test
    public void simpleDefinition() throws DeploymentVerificationException {
        Deployment deployment = new Deployment.DeploymentBuilder()
                // Service Definitions
                .withService("s1")
                    .applicationPath("../sample-multithread/target/multithread-helloworld.jar")
                    .relativeInstrumentableAddress("multithread-helloworld.jar")
                    .exposeAppHomeDirectoryAs("HADOOP_HOME")
                    .runCommand("java -jar ${HADOOP_HOME}/multithread-helloworld.jar")
                    .dockerImage("spidersilk/sample-multithread")
                    .logFile("/var/log/spidersilk")
                    .serviceType(ServiceType.JAVA).and()
                // Node Definitions
                .withNode("n1", "s1")
                    .withStackTraceEvent("e1")
                        .trace("me.arminb.spidersilk.samples.Main.helloWorld1").and()
                    .withStackTraceEvent("e2")
                        .trace("me.arminb.spidersilk.samples.Main.helloWorld2").and()
                    .withStackTraceEvent("e3")
                        .trace("me.arminb.spidersilk.samples.Main.helloWorld3").and()
                    .withSchedulingEvent("bbe2")
                        .operation(SchedulingOperation.BLOCK)
                        .before("e2").and()
                    .withSchedulingEvent("ubbe2")
                        .operation(SchedulingOperation.UNBLOCK)
                        .before("e2").and()
                    .withGarbageCollectionEvent("g1").and()
                    .and()
                .withNode("n2", "s1").offOnStartup()
                .and()
                // Workload Definitions
                .withWorkload("w1")
                    .runCommand("cmd3").and()
                // External Events Definitions
                .withNodeOperationEvent("x1")
                    .nodeName("n2")
                    .nodeOperation(NodeOperation.START).and()
                .withNodeOperationEvent("x2")
                    .nodeName("n2")
                    .nodeOperation(NodeOperation.RESET).and()
                .withNetworkOperationEvent("net1")
                    .networkOperation(NetworkOperation.PARTITION)
                    .nodePartitions("n1,n2").and()
                .withNetworkOperationEvent("net2")
                    .networkOperation(NetworkOperation.REMOVE_PARTITION).and()
                // Run Sequence Definition
                .runSequence("bbe2 * e1 * ubbe2 * x1 * e2 * net1 * e3 * net2 * x2")
                .secondsToWaitForCompletion(5)
                .build();

        SpiderSilkRunner runner = SpiderSilkRunner.run(deployment, new SingleNodeRuntimeEngine(deployment));
        runner.waitForRunSequenceCompletion(true);
    }
}
