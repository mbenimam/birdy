package me.birdy.auth;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class SessionResource {

    @Inject
    private SessionService sessions;

    @Inject
    private CurrentUser currentUser;

    @POST
    @Public
    public Response login(@Valid @NotNull LoginRequest request) {
        return Response.status(Response.Status.CREATED).entity(sessions.login(request)).build();
    }

    @DELETE
    @Path("/current")
    public void logout() {
        sessions.logout(currentUser.get());
    }
}
