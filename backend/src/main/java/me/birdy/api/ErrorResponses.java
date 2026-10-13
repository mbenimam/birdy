package me.birdy.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/** Builds the JSON error responses of the contract. */
public final class ErrorResponses {

    private ErrorResponses() {
    }

    public static Response of(int status, ErrorCode code, String message, Map<String, String> fields) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ApiError(code.wireName(), message, fields))
                .build();
    }

    public static Response of(ErrorCode code, String message) {
        return of(code.status(), code, message, null);
    }

    public static Response validation(String message, Map<String, String> fields) {
        return of(ErrorCode.VALIDATION_FAILED.status(), ErrorCode.VALIDATION_FAILED, message, fields);
    }
}
