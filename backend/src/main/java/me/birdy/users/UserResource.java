package me.birdy.users;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import me.birdy.api.Paging;
import me.birdy.auth.CurrentUser;
import me.birdy.auth.Public;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class UserResource {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    @Inject
    private UserService users;

    @Inject
    private FollowService follows;

    @Inject
    private CurrentUser currentUser;

    @POST
    @Public
    public Response signup(@Valid @NotNull SignupRequest request) {
        return Response.status(Response.Status.CREATED).entity(users.signup(request)).build();
    }

    @GET
    public List<UserSummary> search(@QueryParam("q") String query, @QueryParam("limit") String limit) {
        return users.search(query, Paging.limit(limit, DEFAULT_LIMIT, MAX_LIMIT), currentUser.id());
    }

    @GET
    @Path("/me")
    public UserView me() {
        return users.ownProfile(currentUser.id());
    }

    @DELETE
    @Path("/me")
    public void deleteAccount() {
        users.delete(currentUser.id());
    }

    @PUT
    @Path("/me/following/{username}")
    public void follow(@PathParam("username") String username) {
        follows.follow(currentUser.id(), username);
    }

    @DELETE
    @Path("/me/following/{username}")
    public void unfollow(@PathParam("username") String username) {
        follows.unfollow(currentUser.id(), username);
    }

    @GET
    @Path("/{username}")
    public UserView profile(@PathParam("username") String username) {
        return users.profile(username, currentUser.id());
    }

    @GET
    @Path("/{username}/following")
    public List<UserSummary> following(@PathParam("username") String username) {
        return follows.following(username, currentUser.id());
    }

    @GET
    @Path("/{username}/followers")
    public List<UserSummary> followers(@PathParam("username") String username) {
        return follows.followers(username, currentUser.id());
    }
}
