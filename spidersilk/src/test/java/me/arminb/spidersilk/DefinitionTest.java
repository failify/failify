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

import me.arminb.spidersilk.dsl.ReferableDeploymentEntity;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.ServiceType;
import me.arminb.spidersilk.dsl.events.external.NodeOperation;
import me.arminb.spidersilk.dsl.events.internal.SchedulingOperation;
import me.arminb.spidersilk.exceptions.DeploymentVerificationException;
import me.arminb.spidersilk.execution.RunMode;
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
                    .applicationAddress("pom.xml")
                    .runCommand("cmd1")
                    .libDir("libDir1")
                    .serviceType(ServiceType.JAVA).and()
                // Node Definitions
                .withNode("n1", "s1")
                    .withStackTraceEvent("e1")
                        .trace("me.armib.Main1.method1")
                        .trace("me.arminb.Main2.method2")
                        .trace("me.arminb.Main3.method3").and()
                    .withSchedulingEvent("b1")
                        .operation(SchedulingOperation.BLOCK)
                        .after("e1").and()
                    .withSchedulingEvent("ub1")
                        .operation(SchedulingOperation.UNBLOCK)
                        .after("e1").and()
                    .withGarbageCollectionEvent("g1").and()
                    .runCommand("cmd1-1").and()
                .withNode("n2", "s1").and()
                // Workload Definitions
                .withWorkload("w1")
                    .runCommand("cmd3").and()
                // External Events Definitions
                .withNodeOperationEvent("x1")
                    .nodeName("n1")
                    .nodeOperation(NodeOperation.DOWN).and()
                // Run Sequence Definition
                .runSequence("(w1 | e1) * (b1 | ( x1 * g1 )) * ub1")
                .build();
        logger.info("deployment created.");

        SpiderSilkRunner.run(deployment, RunMode.SINGLE_NODE);

        for (ReferableDeploymentEntity entity: deployment.getReferableDeploymentEntities().values()) {
            System.out.println(entity.getName() + " -> " + entity.getDependsOn());
        }
    }
}
