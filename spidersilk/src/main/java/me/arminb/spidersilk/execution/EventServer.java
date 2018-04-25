package me.arminb.spidersilk.execution;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventServer {
    private final static Logger logger = LoggerFactory.getLogger(EventServer.class);
    private final Deployment deployment;
    private Server jettyServer;

    public EventServer(Deployment deployment) {
        this.deployment = deployment;
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        jettyServer = new Server(deployment.getEventServerPortNumber());
        jettyServer.setHandler(context);

        ServletHolder jerseyServlet = context.addServlet(
                org.glassfish.jersey.servlet.ServletContainer.class, "/*");
        jerseyServlet.setInitOrder(0);

        jerseyServlet.setInitParameter(
                "jersey.config.server.provider.classnames",
                JerseyEndPoint.class.getCanonicalName());
    }

    public void start() throws RuntimeEngineException {
        try {
            jettyServer.start();
        } catch (Exception e) {
            throw new RuntimeEngineException("Cannot start Jetty Server on port " + deployment.getEventServerPortNumber() + "!");
        }
    }

    public void stop() {
        try {
            jettyServer.stop();
            jettyServer.destroy();
        } catch (Exception e) {
            logger.error("Unable to stop Jetty Server!", e);
        }
    }

    public void join() {
        try {
            jettyServer.join();
        } catch (InterruptedException e) {
            logger.error("Jetty Server has been interrupted!", e);
        }
    }


    public Deployment getDeployment() {
        return deployment;
    }
}
