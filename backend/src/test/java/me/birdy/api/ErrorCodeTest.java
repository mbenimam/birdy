package me.birdy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void codesHaveTheStatusAndTheWireNameOfTheContract() {
        assertEquals(Map.of(
                "validation_failed", 400,
                "unauthorized", 401,
                "forbidden", 403,
                "not_found", 404,
                "conflict", 409,
                "internal_error", 500),
                Map.of(
                        ErrorCode.VALIDATION_FAILED.wireName(), ErrorCode.VALIDATION_FAILED.status(),
                        ErrorCode.UNAUTHORIZED.wireName(), ErrorCode.UNAUTHORIZED.status(),
                        ErrorCode.FORBIDDEN.wireName(), ErrorCode.FORBIDDEN.status(),
                        ErrorCode.NOT_FOUND.wireName(), ErrorCode.NOT_FOUND.status(),
                        ErrorCode.CONFLICT.wireName(), ErrorCode.CONFLICT.status(),
                        ErrorCode.INTERNAL_ERROR.wireName(), ErrorCode.INTERNAL_ERROR.status()));
        assertEquals(6, ErrorCode.values().length);
    }

    @Test
    void aKnownStatusMapsToItsCode() {
        assertEquals(ErrorCode.NOT_FOUND, ErrorCode.forStatus(404));
        assertEquals(ErrorCode.UNAUTHORIZED, ErrorCode.forStatus(401));
    }

    @Test
    void otherStatusesMapToTheGenericClientOrServerCode() {
        assertEquals(ErrorCode.VALIDATION_FAILED, ErrorCode.forStatus(405));
        assertEquals(ErrorCode.VALIDATION_FAILED, ErrorCode.forStatus(415));
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.forStatus(503));
    }

    @Test
    void onlyValidationFailuresCarryFields() {
        assertEquals(Map.of("name", "is wrong"), ApiException.invalid("name", "is wrong").fields());
        assertNull(ApiException.notFound("gone").fields());
        assertNull(ApiException.forbidden("no").fields());
        assertNull(ApiException.conflict("taken").fields());
        assertNull(ApiException.unauthorized("who").fields());
        assertEquals(ErrorCode.FORBIDDEN, ApiException.forbidden("no").code());
        assertEquals("taken", ApiException.conflict("taken").getMessage());
    }
}
