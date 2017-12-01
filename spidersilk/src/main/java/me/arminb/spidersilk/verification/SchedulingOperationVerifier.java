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
import me.arminb.spidersilk.dsl.events.internal.SchedulingEvent;
import me.arminb.spidersilk.dsl.events.internal.SchedulingOperation;
import me.arminb.spidersilk.exceptions.DeploymentVerificationException;

import java.util.HashMap;

/**
 * verifies the deployment object to include only matched block and unblock events
 */
public class SchedulingOperationVerifier extends DeploymentVerifier {
    public SchedulingOperationVerifier(Deployment deployment) {
        super(deployment);
    }

    @Override
    public void verify() {
        HashMap<String, Boolean> blockOccurrenceMap = new HashMap<>();
        HashMap<String, Boolean> eventsMap = new HashMap<>();

        for (String id: deployment.getRunSequence().split("\\W")) {
            eventsMap.put(id, true);
            if (deployment.getReferableDeploymentEntity(id) instanceof SchedulingEvent) {
                SchedulingEvent event = (SchedulingEvent) deployment.getReferableDeploymentEntity(id);
                if (event.getOperation().equals(SchedulingOperation.BLOCK)) {
                    if(blockOccurrenceMap.containsKey(event.getTargetEventName() + event.getSchedulingPoint())) {
                        throw new DeploymentVerificationException("Blocking twice on the same event is not possible before unblocking it first");
                    } else {
                        blockOccurrenceMap.put(event.getTargetEventName() + event.getSchedulingPoint(), true);
                    }
                } else if (event.getOperation().equals(SchedulingOperation.UNBLOCK)){
                    blockOccurrenceMap.remove(event.getTargetEventName() + event.getSchedulingPoint());
                }
            }
        }

        if (!blockOccurrenceMap.isEmpty()) {
            throw new DeploymentVerificationException("Unblock events are needed for events" + blockOccurrenceMap.keySet().toString());
        }
    }
}
