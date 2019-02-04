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

package io.failify.dsl.events.external;

import io.failify.execution.LimitedRuntimeEngine;
import io.failify.dsl.entities.Deployment;
import io.failify.dsl.events.ExternalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an external event to impose a clock drift in a specific node
 */
public class ClockDriftEvent extends ExternalEvent {

    private static final Logger logger = LoggerFactory.getLogger(ClockDriftEvent.class);

    private final String nodeName; // the node name to apply the clock drift on
    private final Integer amount; // the positive or negative amount of time offset to apply in milliseconds

    /**
     * Constructor
     * @param builder the builder instance to use for creating the class instance
     */
    protected ClockDriftEvent(Builder builder) {
        super(builder.getName());
        nodeName = builder.nodeName;
        amount = builder.amount;
    }

    @Override
    protected void execute(LimitedRuntimeEngine runtimeEngine) throws Exception {
        runtimeEngine.clockDrift(nodeName, amount);
    }

    /**
     * The builder class for building a clock drift event
     */
    public static class Builder extends BuilderBase<ClockDriftEvent, Deployment.Builder> {

        private String nodeName;
        private Integer amount;

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param name the name of the clock drift event to be built
         */
        public Builder(Deployment.Builder parentBuilder, String name) {
            super(parentBuilder, name);
        }

        /**
         * Constructor
         * @param parentBuilder the parent builder object for this builder
         * @param instance a clock drift event object instance to be changed
         */
        public Builder(Deployment.Builder parentBuilder, ClockDriftEvent instance) {
            super(parentBuilder, instance);
            nodeName = new String(instance.nodeName);
            amount = new Integer(instance.amount);
        }

        /**
         * Sets the node name to apply the clock drift on
         * @param nodeName the node name to apply the clock drift on
         * @return the current builder instance
         */
        public Builder nodeName(String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        /**
         * Sets the positive or negative amount of time offset to apply in milliseconds
         * @param amount the positive or negative amount of time offset to apply in milliseconds
         * @return the current builder instance
         */
        public Builder amount(Integer amount) {
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
