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

import me.arminb.spidersilk.dsl.ReferableDeploymentEntity;
import me.arminb.spidersilk.execution.RuntimeEngine;
import me.arminb.spidersilk.rt.SpiderSilk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the base class for external events which implements Template Method pattern. The child classes should implement
 * whatever they need to do in the execute method and this class will take care of satisfying the dependencies and let the
 * event server know about the completion of the corresponding event of the child class
 */
public abstract class ExternalEvent extends ReferableDeploymentEntity {
    private static Logger logger = LoggerFactory.getLogger(ExternalEvent.class);
    private Thread executionThread;

    protected ExternalEvent(String name) {
        super(name);
    }


    protected abstract void execute(RuntimeEngine runtimeEngine);

    public void start(RuntimeEngine runtimeEngine) {
        executionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                SpiderSilk.getInstance().blockAndPoll(name);
                execute(runtimeEngine);
                SpiderSilk.getInstance().sendEvent(name);
            }
        });
        logger.info("Starting external event {}", name);
        executionThread.start();
    }

    public void stop() {
        if (executionThread != null) {
            executionThread.stop();
        }
    }
}
