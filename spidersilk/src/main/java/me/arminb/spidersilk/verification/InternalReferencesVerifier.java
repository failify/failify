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

package me.arminb.spidersilk.verification;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.entities.Node;
import me.arminb.spidersilk.dsl.events.ExternalEvent;
import me.arminb.spidersilk.dsl.events.InternalEvent;
import me.arminb.spidersilk.dsl.events.external.NodeOperationEvent;
import me.arminb.spidersilk.dsl.events.internal.SchedulingEvent;
import me.arminb.spidersilk.dsl.events.internal.StackTraceEvent;
import me.arminb.spidersilk.exceptions.DeploymentEntityBadReferenceException;
import me.arminb.spidersilk.exceptions.DeploymentEntityNotFound;

public class InternalReferencesVerifier extends DeploymentVerifier {

    public InternalReferencesVerifier(Deployment deployment) {
        super(deployment);
    }

    @Override
    public void verify() {
        verifyInternalEventReferences();
        verifyNodeReferences();
        verifyServiceReferences();
    }

    private void verifyServiceReferences() {
        for (Node node: deployment.getNodes().values()) {
            if (deployment.getService(node.getServiceName()) == null) {
                throw new DeploymentEntityBadReferenceException("service", node.getServiceName(), "node", node.getName());
            }
        }
    }

    private void verifyNodeReferences() {
        for (ExternalEvent externalEvent: deployment.getExecutableEntities().values()) {
            if (externalEvent instanceof NodeOperationEvent) {
                NodeOperationEvent nodeOperationEvent = (NodeOperationEvent) externalEvent;
                if (deployment.getNode(nodeOperationEvent.getNodeName()) == null) {
                    throw new DeploymentEntityBadReferenceException("node", nodeOperationEvent.getNodeName(), "node operation event",
                            nodeOperationEvent.getName());
                }
            }
        }
    }

    private void verifyInternalEventReferences() {
        for (Node node: deployment.getNodes().values()) {
            for (InternalEvent internalEvent: node.getInternalEvents().values()) {
                if (internalEvent instanceof SchedulingEvent) {
                    SchedulingEvent schedulingEvent = (SchedulingEvent) internalEvent;
                    InternalEvent targetEvent = node.getInternalEvent(schedulingEvent.getTargetEventName());
                    if (targetEvent == null || !(targetEvent instanceof StackTraceEvent)) {
                        throw new DeploymentEntityBadReferenceException("stack trace event", schedulingEvent.getTargetEventName(),
                                "scheduling event", schedulingEvent.getName());
                    }
                }
            }
        }
    }
}
