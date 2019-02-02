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
 *
 */

package me.arminb.spidersilk.dsl.events.internal;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.events.InternalEvent;

/**
 * This is base class for all of the internal events that can potentially be blocking e.g. stack trace and scheduling events
 */
public abstract class BlockingEvent extends InternalEvent {
    protected String blockingCondition; // this condition contains a set of events that need to be satisfied for this
                                        // event to be blocked

    protected BlockingEvent(String name, String nodeName) {
        super(name, nodeName);
    }

    public String getBlockingCondition() {
        return blockingCondition;
    }

    public void setBlockingCondition(String blockingCondition) {
        this.blockingCondition = this.blockingCondition == null ? blockingCondition : throwBlockingConditionIsSet_();
    }

    private String throwBlockingConditionIsSet_() {
        throw new RuntimeException("blockingCondition is already set");
    }

    /**
     * Subclasses should override this method if they have an operation that may not be blocking
     * @return
     */
    public boolean isBlocking() {
        return true;
    }

    /**
     * This method should be implemented by all of the subclasses and will be used in the verifier
     * @param deployment the deployment definition
     * @return the stack trace for this blocking event
     */
    public abstract String getStack(Deployment deployment);

    /**
     * This method should be implemented by all of the subclasses and will be used in the verifier
     * @return the blocking point (before/after) of the event
     */
    public abstract SchedulingPoint getSchedulingPoint();

}
