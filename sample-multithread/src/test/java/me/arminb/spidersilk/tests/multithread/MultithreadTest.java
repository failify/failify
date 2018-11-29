/*
 * MIT License
 *
 * Copyright (c) 2017-2018 Armin Balalaie
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
 */

package me.arminb.spidersilk.tests.multithread;

import me.arminb.spidersilk.SpiderSilkRunner;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.ServiceType;
import me.arminb.spidersilk.exceptions.DeploymentVerificationException;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.execution.single_node.SingleNodeRuntimeEngine;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultithreadTest {
    public static final Logger logger = LoggerFactory.getLogger(MultithreadTest.class);

    @Test
    public void simpleDefinition() throws DeploymentVerificationException, RuntimeEngineException {
        Deployment deployment = new Deployment.DeploymentBuilder("sample-multithread")
                // Service Definitions
                .withServiceFromJvmClasspath("s1", "target/classes", "**commons-io*.jar")
                    .startCommand("java -cp ${SPIDERSILK_JVM_CLASSPATH} me.arminb.spidersilk.samples.multithread.Main")
                    .dockerImage("spidersilk/sample-multithread")
                    .dockerFileAddress("../sample-multithread/docker/Dockerfile", false)
                    .logFile("/var/log/sample1")
                    .logDirectory("/var/log/samples")
                    .serviceType(ServiceType.JAVA).and()
                // Node Definitions
                .withNode("n1", "s1")
                    .stackTrace("e1", "me.arminb.spidersilk.samples.multithread.Main.helloWorld1")
                    .stackTrace("e2", "me.arminb.spidersilk.samples.multithread.Main.helloWorld2")
                    .stackTrace("e3", "me.arminb.spidersilk.samples.multithread.Main.helloWorld3")
                    .stackTrace("e4", "org.apache.commons.io.FilenameUtils.normalize")
                    .blockBefore("bbe2", "e2")
                    .unblockBefore("ubbe2", "e2")
                    .garbageCollection("g1")
                    .and()
                .withNode("n2", "s1").offOnStartup()
                .and()
                // Workload Events
                .workloadEvents("we1", "we2")
                // External Events Definitions
                .startNode("x1", "n2")
                .restartNode("x2", "n2")
                // Run Sequence Definition
                .runSequence("bbe2 * e1 * ubbe2 * x1 * e2 * we1 * e3 * we2 * x2 * e4")
                .sharedDirectory("/spidersilk")
                .nextEventReceiptTimeout(15)
                .build();

        SpiderSilkRunner runner = SpiderSilkRunner.run(deployment, new SingleNodeRuntimeEngine(deployment));
        // Injecting network partition in a specific time in the test case
        runner.runtime().waitFor("x1");
        runner.runtime().networkPartition("n1,n2");
        runner.runtime().clockDrift("n1", -1000);
        runner.runtime().sendEvent("we1");
        // Removing network partition in a specific time in the test case
        runner.runtime().enforceOrder("we2", () -> runner.runtime().removeNetworkPartition());
        runner.waitForRunSequenceCompletion(true);
    }
}
