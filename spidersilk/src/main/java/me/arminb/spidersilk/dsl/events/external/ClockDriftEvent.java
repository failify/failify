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

package me.arminb.spidersilk.dsl.events.external;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.events.ExternalEvent;
import me.arminb.spidersilk.execution.LimitedRuntimeEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClockDriftEvent extends ExternalEvent {

    private static final Logger logger = LoggerFactory.getLogger(ClockDriftEvent.class);

    private final String nodeName;
    private final Integer amount;

    protected ClockDriftEvent(ClockDriftEventBuilder builder) {
        super(builder.getName());
        nodeName = builder.nodeName;
        amount = builder.amount;
    }

    @Override
    protected void execute(LimitedRuntimeEngine runtimeEngine) throws Exception {
        runtimeEngine.clockDrift(nodeName, amount);
    }

    public static class ClockDriftEventBuilder extends DeploymentBuilderBase<ClockDriftEvent, Deployment.DeploymentBuilder> {

        private String nodeName;
        private Integer amount;

        public ClockDriftEventBuilder(Deployment.DeploymentBuilder parentBuilder, String name) {
            super(parentBuilder, name);
        }

        public ClockDriftEventBuilder(Deployment.DeploymentBuilder parentBuilder, ClockDriftEvent instance) {
            super(parentBuilder, instance);
            nodeName = new String(instance.nodeName);
            amount = new Integer(instance.amount);
        }

        public ClockDriftEventBuilder nodeName(String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        public ClockDriftEventBuilder amount(Integer amount) {
            this.amount = amount;
            return this;
        }

        @Override
        public ClockDriftEvent build() {
            return new ClockDriftEvent(this);
        }

        @Override
        protected void returnToParent(ClockDriftEvent builtObj) {
            parentBuilder.clockDriftEvent(builtObj);
        }
    }
}
