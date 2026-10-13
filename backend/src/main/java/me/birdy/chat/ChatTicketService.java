package me.birdy.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import me.birdy.auth.Tokens;
import me.birdy.users.UserRef;

/**
 * Issues the short-lived, single-use tickets that authenticate WebSocket connections. Browsers cannot send an
 * {@code Authorization} header on a WebSocket handshake, and a ticket keeps the long-lived session token out of URLs.
 *
 * <p>Tickets are kept in memory, keyed by their digest: they live 30 seconds and need not survive a restart.
 */
@ApplicationScoped
public class ChatTicketService {

    static final Duration TTL = Duration.ofSeconds(30);

    private record Ticket(UserRef user, Instant expiresAt) {
    }

    private final Map<String, Ticket> pending = new ConcurrentHashMap<>();

    private Tokens tokens;
    private Clock clock;

    protected ChatTicketService() {
        // For the client proxy of the container.
    }

    @Inject
    public ChatTicketService(Tokens tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    public TicketView issue(UserRef user) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        pending.values().removeIf(ticket -> !now.isBefore(ticket.expiresAt()));
        String ticket = tokens.newToken();
        Instant expiresAt = now.plus(TTL);
        pending.put(tokens.digest(ticket), new Ticket(user, expiresAt));
        return new TicketView(ticket, expiresAt);
    }

    /** Consumes a ticket: returns its user the first time it is presented before expiry, empty in all other cases. */
    public Optional<UserRef> redeem(String ticket) {
        if (ticket == null || ticket.length() != Tokens.TOKEN_LENGTH) {
            return Optional.empty();
        }
        Ticket found = pending.remove(tokens.digest(ticket));
        if (found == null || !clock.instant().isBefore(found.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(found.user());
    }
}
