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

package me.arminb.spidersilk.dsl.events;

import me.arminb.spidersilk.dsl.DeploymentEntity;
import me.arminb.spidersilk.dsl.Instrumentable;
import me.arminb.spidersilk.dsl.ReferableDeploymentEntity;
import me.arminb.spidersilk.dsl.entities.Node;

/**
 * This is the base class for all internal events which adds a node name to all of them for future reference in next phases
 */
public abstract class InternalEvent extends ReferableDeploymentEntity implements Instrumentable{
    protected final String nodeName;

    protected InternalEvent(String name, String nodeName) {
        super(name);
        this.nodeName = nodeName;
    }

    public String getNodeName() {
        return nodeName;
    }

    public static abstract class InternalEventBuilder<S extends InternalEvent> extends DeploymentBuilderBase<S, Node.NodeBuilder> {
        protected final String nodeName;

        public InternalEventBuilder(Node.NodeBuilder parentBuilder, String name, String nodeName) {
            super(parentBuilder, name);
            this.nodeName = nodeName;
        }

        public InternalEventBuilder(Node.NodeBuilder parentBuilder, InternalEvent instance) {
            super(parentBuilder, instance);
            nodeName = new String(instance.nodeName);
        }

        public String getNodeName() {
            return nodeName;
        }
    }
}
