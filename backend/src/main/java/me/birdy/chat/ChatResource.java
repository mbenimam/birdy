package me.birdy.chat;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
import me.birdy.users.UserRef;

@Path("/chat")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class ChatResource {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    @Inject
    private ChatTicketService tickets;

    @Inject
    private MessageStore messages;

    @Inject
    private CurrentUser currentUser;

    @POST
    @Path("/tickets")
    public Response issueTicket() {
        TicketView ticket = tickets.issue(new UserRef(currentUser.id(), currentUser.username()));
        return Response.status(Response.Status.CREATED).entity(ticket).build();
    }

    @GET
    @Path("/rooms/{room}/messages")
    public List<ChatMessageView> history(@PathParam("room") String room, @QueryParam("limit") String limit) {
        if (!RoomNames.isValid(room)) {
            throw ApiException.invalid("room", "must be 1 to 32 lower-case letters, digits or hyphens");
        }
        return messages.lastMessages(room, Paging.limit(limit, DEFAULT_LIMIT, MAX_LIMIT));
    }
}
