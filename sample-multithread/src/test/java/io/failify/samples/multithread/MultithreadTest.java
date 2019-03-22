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
 */

package io.failify.samples.multithread;

import io.failify.FailifyRunner;
import io.failify.dsl.entities.Deployment;
import io.failify.dsl.entities.ServiceType;
import io.failify.exceptions.DeploymentVerificationException;
import io.failify.exceptions.RuntimeEngineException;
import io.failify.execution.NetOp;
import io.failify.execution.NetPart;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

public class MultithreadTest {
    public static final Logger logger = LoggerFactory.getLogger(MultithreadTest.class);

    @Test
    public void simpleDefinition() throws DeploymentVerificationException, RuntimeEngineException, TimeoutException {
        Deployment deployment = Deployment.builder("sample-multithread")
                // Service Definitions
                .withServiceFromJvmClasspath("s1", "target/classes", "**commons-io*.jar")
                    .startCommand("java -cp ${FAILIFY_JVM_CLASSPATH} io.failify.samples.multithread.Main")
                    .dockerImageName("failify/sample-multithread")
                    .dockerFileAddress("../sample-multithread/docker/Dockerfile", false)
                    .logFile("/var/log/sample1")
                    .logDirectory("/var/log/samples")
                    .serviceType(ServiceType.JAVA).and()
                // Node Definitions
                .withNode("n1", "s1")
                    .stackTrace("e1", "io.failify.samples.multithread.Main.helloWorld1")
                    .stackTrace("e2", "io.failify.samples.multithread.Main.helloWorld2")
                    .stackTrace("e3", "io.failify.samples.multithread.Main.helloWorld3")
                    .stackTrace("e4", "org.apache.commons.io.FilenameUtils.normalize")
                    .blockBefore("bbe2", "e2")
                    .unblockBefore("ubbe2", "e2")
                    .garbageCollection("g1")
                    .and()
                .withNode("n2", "s1").offOnStartup().and()
                .withNode("n3", "s1").and()
                .withNode("n4", "s1").and()
                .withNode("n5", "s1").and()
                // Test Case Events
                .testCaseEvents("x1", "x2")
                // Run Sequence Definition
                .runSequence("bbe2 * e1 * ubbe2 * x1 * e2 * e3 * x2 * e4")
                .sharedDirectory("/failify")
                .build();

        FailifyRunner runner = FailifyRunner.run(deployment);
        // Starting node n2
        runner.runtime().enforceOrder("x1",15, () -> runner.runtime().startNode("n2"));
        // Imposing overlapping network partitions
        NetPart netPart1 = NetPart.partitions("n1","n2").connect(1, NetPart.REST, false).build();
        NetPart netPart2 = NetPart.partitions("n1","n2,n3").connect(1, NetPart.REST).build();
        runner.runtime().networkPartition(netPart1);
        runner.runtime().networkPartition(netPart2);
        // Imposing 10 secs of clock drift in node n1
        runner.runtime().clockDrift("n1", -10000);
        // Applying network delay and loss on node n2 before restarting it
        runner.runtime().networkOperation("n2", NetOp.delay(50).jitter(10), NetOp.loss(30));
        // removing the first network partition and restarting node n2
        runner.runtime().enforceOrder("x2", 10, () -> {
            runner.runtime().removeNetworkPartition(netPart1);
            runner.runtime().restartNode("n2", 10);
        });
        // removing the second network partition
        runner.runtime().removeNetworkPartition(netPart2);
        // Applying different kinds of network operations in different orders
        runner.runtime().networkOperation("n1", NetOp.delay(100).jitter(10), NetOp.loss(30),
                NetOp.removeDelay(), NetOp.delay(10).jitter(4), NetOp.removeLoss(),
                NetOp.removeDelay(), NetOp.loss(20), NetOp.removeLoss());
        // Waiting for the run sequence to be completed
        runner.waitForRunSequenceCompletion(60, 20, true);
    }
}
