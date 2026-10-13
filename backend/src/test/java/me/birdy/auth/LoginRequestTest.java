package me.birdy.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import me.birdy.api.Validation;
import org.junit.jupiter.api.Test;

class LoginRequestTest {

    @Test
    void usernameAndPasswordAreAccepted() {
        assertEquals(Set.of(), Validation.invalidFields(new LoginRequest("alice", "any password")));
    }

    @Test
    void bothPropertiesAreRequired() {
        assertEquals(Set.of("username", "password"), Validation.invalidFields(new LoginRequest(null, null)));
        assertEquals(Set.of("username", "password"), Validation.invalidFields(new LoginRequest(" ", "")));
    }

    @Test
    void oversizedValuesAreRejectedBeforeAnyHashing() {
        assertEquals(Set.of("username"), Validation.invalidFields(new LoginRequest("a".repeat(31), "password")));
        assertEquals(Set.of("password"), Validation.invalidFields(new LoginRequest("alice", "p".repeat(129))));
        assertEquals(Set.of(), Validation.invalidFields(new LoginRequest("a".repeat(30), "p".repeat(128))));
    }
}
