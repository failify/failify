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

package me.arminb.spidersilk.deployment;

public class Node extends DeploymentBase {
    private final String serviceName;

    public Node(NodeBuilder builder) {
        super(builder.name);
        serviceName = builder.serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public static class NodeBuilder extends DeploymentBuilderBase<Node, Deployment.DeploymentBuilder> {
        private String serviceName;

        public NodeBuilder(Deployment.DeploymentBuilder parentBuilder, String name) {
            super(parentBuilder, name);
        }

        public NodeBuilder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        @Override
        public Node build() {
            return new Node(this);
        }

        @Override
        protected void returnToParent(Node builtObj) {
            parentBuilder.node(builtObj);
        }
    }
}
