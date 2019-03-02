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

package io.failify.execution;

import io.failify.exceptions.RuntimeEngineException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventServer {
    private final static Logger logger = LoggerFactory.getLogger(EventServer.class);
    private Server jettyServer;
    private Integer portNumber;
    private Boolean stopped;

    public EventServer() {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        jettyServer = new Server(0);
        jettyServer.setHandler(context);

        ServletHolder jerseyServlet = context.addServlet(
                org.glassfish.jersey.servlet.ServletContainer.class, "/*");
        jerseyServlet.setInitOrder(0);

        jerseyServlet.setInitParameter(
                "jersey.config.server.provider.classnames",
                JerseyEndPoint.class.getCanonicalName());
        stopped = true;
    }

    public void start() throws RuntimeEngineException {
        if (stopped) {
            try {
                jettyServer.start();
                portNumber = ((ServerConnector) jettyServer.getConnectors()[0]).getLocalPort();
                stopped = false;
            } catch (Exception e) {
                throw new RuntimeEngineException("Cannot start Jetty Server!", e);
            }
        }
    }

    public void stop() {
        if (!stopped) {
            try {
                jettyServer.stop();
                jettyServer.destroy();
                stopped = true;
            } catch (Exception e) {
                logger.error("Unable to stop Jetty Server!", e);
            }
        }
    }

    public Integer getPortNumber() {
        return portNumber;
    }
}
