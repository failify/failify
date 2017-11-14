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

import me.arminb.spidersilk.deployment.Deployment;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefinitionTest {
    public static final Logger logger = LoggerFactory.getLogger(DefinitionTest.class);

    @Test
    public void simpleDefinition() {
        Deployment deployment = new Deployment.DeploymentBuilder()
                .withService("s1")
                    .jarFile("jar1")
                    .runCommand("cmd1")
                    .withEvent("e1", "me.arminb.Main")
                    .withEvent("e2", "me.arminb.Main2")
                .and()
                .withService("s2")
                    .jarFile("jar2")
                    .runCommand("cmd2")
                    .withEvent("e1", "me.arminb.Main3")
                .and()
                .withNode("n1")
                    .serviceName("s1")
                .and()
                .withWorkload("w1")
                    .runCommand("cmd3")
                .and()
                .runSequence("am bm cm dm")
                .build();
        logger.info("deployment created.");
    }
}
