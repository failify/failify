package me.arminb.spidersilk.execution;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
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
