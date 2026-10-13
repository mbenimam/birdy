package me.birdy.users;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Set;
import me.birdy.api.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SignupRequestTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 5, 17);

    private static SignupRequest request(String username, String email, String password, LocalDate birthday) {
        return new SignupRequest(username, email, password, birthday);
    }

    @Test
    void aCompleteRequestIsValid() {
        assertEquals(Set.of(), Validation.invalidFields(request("alice_01", "alice@example.org", "12345678", BIRTHDAY)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "A_b_9", "user_name_with_thirty_chars_00", "___"})
    void usernamesOfThreeToThirtyWordCharactersAreAccepted(String username) {
        assertEquals(Set.of(), Validation.invalidFields(request(username, "a@example.org", "12345678", BIRTHDAY)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ab", "user_name_with_thirty_one_chars", "with space", "dash-ed", "émile", "a.b",
            "abc\n", "<script>"})
    void otherUsernamesAreRejected(String username) {
        assertEquals(Set.of("username"),
                Validation.invalidFields(request(username, "a@example.org", "12345678", BIRTHDAY)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "no-at-sign", "two@@example.org", "trailing@"})
    void malformedEmailsAreRejected(String email) {
        assertEquals(Set.of("email"), Validation.invalidFields(request("alice", email, "12345678", BIRTHDAY)));
    }

    @Test
    void anEmailLongerThanTheColumnIsRejected() {
        String email = "a".repeat(64) + "@" + "d".repeat(63) + "." + "e".repeat(63) + "." + "f".repeat(63) + ".org";

        assertEquals(Set.of("email"), Validation.invalidFields(request("alice", email, "12345678", BIRTHDAY)));
    }

    @Test
    void theMessageForAMalformedEmailIsTheOneOfTheContract() {
        assertEquals("must be a well-formed email address",
                Validation.problems(request("alice", "nope", "12345678", BIRTHDAY)).get("email"));
    }

    @Test
    void passwordsMustHaveEightToOneHundredTwentyEightCharacters() {
        assertEquals(Set.of("password"), Validation.invalidFields(request("alice", "a@example.org", null, BIRTHDAY)));
        assertEquals(Set.of("password"),
                Validation.invalidFields(request("alice", "a@example.org", "1234567", BIRTHDAY)));
        assertEquals(Set.of("password"),
                Validation.invalidFields(request("alice", "a@example.org", "p".repeat(129), BIRTHDAY)));
        assertEquals(Set.of(), Validation.invalidFields(request("alice", "a@example.org", "p".repeat(128), BIRTHDAY)));
    }

    @Test
    void theBirthdayMustBeInThePast() {
        assertEquals(Set.of("birthday"), Validation.invalidFields(request("alice", "a@example.org", "12345678", null)));
        assertEquals(Set.of("birthday"),
                Validation.invalidFields(request("alice", "a@example.org", "12345678", LocalDate.now().plusDays(1))));
        assertEquals(Set.of(),
                Validation.invalidFields(request("alice", "a@example.org", "12345678", LocalDate.now().minusDays(1))));
    }
}
