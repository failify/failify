package me.arminb.spidersilk.execution;

import me.arminb.spidersilk.dsl.ReferableDeploymentEntity;
import me.arminb.spidersilk.dsl.entities.Deployment;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;

@Path("/")
public class JerseyEndPoint {

    @GET
    @Path("/dependencies/{name}")
    public Response checkEventDependencies(@PathParam("name") String eventName) {
        if (EventService.getInstance().areDependenciesMet(eventName)) {
            return Response.status(Response.Status.OK).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @POST
    @Path("/events")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response receiveEvent(Event event) {
        EventService.getInstance().receiveEvent(event.getName());
        return Response.status(Response.Status.OK).build();
    }

    public static class Event {
        String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
