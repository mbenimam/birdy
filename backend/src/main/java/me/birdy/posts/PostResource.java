package me.birdy.posts;

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
import me.birdy.api.ApiException;
import me.birdy.api.Paging;
import me.birdy.auth.CurrentUser;

@Path("/posts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class PostResource {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String FEED_HOME = "home";
    private static final String FEED_USER = "user";

    @Inject
    private PostService posts;

    @Inject
    private CurrentUser currentUser;

    @POST
    public Response create(@Valid @NotNull CreatePostRequest request) {
        PostView created = posts.create(currentUser.get(), request.content());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    public List<PostView> list(
            @QueryParam("feed") String feed,
            @QueryParam("author") String author,
            @QueryParam("before") String before,
            @QueryParam("limit") String limit) {
        int pageSize = Paging.limit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        if (feed == null || FEED_HOME.equals(feed)) {
            return posts.homeFeed(currentUser.id(), before, pageSize);
        }
        if (!FEED_USER.equals(feed)) {
            throw ApiException.invalid("feed", "must be home or user");
        }
        if (author == null || author.isBlank()) {
            throw ApiException.invalid("author", "is required when feed is user");
        }
        return posts.userFeed(author, currentUser.id(), before, pageSize);
    }

    @GET
    @Path("/{id}")
    public PostView get(@PathParam("id") String id) {
        return posts.get(id, currentUser.id());
    }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") String id) {
        posts.delete(id, currentUser.id());
    }

    @PUT
    @Path("/{id}/like")
    public void like(@PathParam("id") String id) {
        posts.like(id, currentUser.id());
    }

    @DELETE
    @Path("/{id}/like")
    public void unlike(@PathParam("id") String id) {
        posts.unlike(id, currentUser.id());
    }

    @GET
    @Path("/{id}/comments")
    public List<CommentView> comments(@PathParam("id") String id) {
        return posts.comments(id, currentUser.id());
    }

    @POST
    @Path("/{id}/comments")
    public Response addComment(@PathParam("id") String id, @Valid @NotNull CreateCommentRequest request) {
        CommentView created = posts.addComment(id, currentUser.get(), request.content());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @DELETE
    @Path("/{id}/comments/{commentId}")
    public void deleteComment(@PathParam("id") String id, @PathParam("commentId") String commentId) {
        posts.deleteComment(id, commentId, currentUser.id());
    }
}
