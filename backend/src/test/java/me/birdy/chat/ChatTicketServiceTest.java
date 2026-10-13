package me.birdy.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import me.birdy.auth.Tokens;
import me.birdy.users.UserRef;
import org.junit.jupiter.api.Test;

class ChatTicketServiceTest {

    /** A clock the test moves by hand. */
    private static final class ManualClock extends Clock {

        private Instant now = Instant.parse("2026-05-06T07:08:09Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final UserRef ALICE = new UserRef(1, "alice");
    private static final UserRef BOB = new UserRef(2, "bob");

    private final ManualClock clock = new ManualClock();
    private final ChatTicketService tickets = new ChatTicketService(new Tokens(), clock);

    @Test
    void aTicketExpiresThirtySecondsAfterItWasIssued() {
        TicketView ticket = tickets.issue(ALICE);

        assertEquals(clock.instant().plusSeconds(30), ticket.expiresAt());
        assertEquals(Tokens.TOKEN_LENGTH, ticket.ticket().length());
    }

    @Test
    void aTicketIdentifiesTheUserItWasIssuedTo() {
        String forAlice = tickets.issue(ALICE).ticket();
        String forBob = tickets.issue(BOB).ticket();

        assertNotEquals(forAlice, forBob);
        assertEquals(Optional.of(BOB), tickets.redeem(forBob));
        assertEquals(Optional.of(ALICE), tickets.redeem(forAlice));
    }

    @Test
    void aTicketCanBeRedeemedOnlyOnce() {
        String ticket = tickets.issue(ALICE).ticket();

        assertEquals(Optional.of(ALICE), tickets.redeem(ticket));
        assertEquals(Optional.empty(), tickets.redeem(ticket));
        assertEquals(Optional.empty(), tickets.redeem(ticket));
    }

    @Test
    void aTicketIsStillValidJustBeforeItsExpiry() {
        String ticket = tickets.issue(ALICE).ticket();
        clock.advance(Duration.ofSeconds(30).minusMillis(1));

        assertEquals(Optional.of(ALICE), tickets.redeem(ticket));
    }

    @Test
    void anExpiredTicketIsRejected() {
        String ticket = tickets.issue(ALICE).ticket();
        clock.advance(Duration.ofSeconds(30));

        assertEquals(Optional.empty(), tickets.redeem(ticket));
    }

    @Test
    void anExpiredTicketStaysRejectedAfterOtherTicketsWereIssued() {
        String expired = tickets.issue(ALICE).ticket();
        clock.advance(Duration.ofMinutes(5));
        String fresh = tickets.issue(ALICE).ticket();

        assertEquals(Optional.empty(), tickets.redeem(expired));
        assertEquals(Optional.of(ALICE), tickets.redeem(fresh));
    }

    @Test
    void unknownEmptyAndMissingTicketsAreRejected() {
        tickets.issue(ALICE);

        assertEquals(Optional.empty(), tickets.redeem(new Tokens().newToken()));
        assertEquals(Optional.empty(), tickets.redeem("garbage"));
        assertEquals(Optional.empty(), tickets.redeem(""));
        assertEquals(Optional.empty(), tickets.redeem(null));
    }
}
