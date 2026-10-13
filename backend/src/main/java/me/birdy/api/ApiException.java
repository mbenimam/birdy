package me.birdy.api;

import java.util.Map;

/** A failure with a defined place in the API contract; mapped to the error body by {@link ApiExceptionMapper}. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fields;

    private ApiException(ErrorCode code, String message, Map<String, String> fields) {
        super(message);
        this.code = code;
        this.fields = fields;
    }

    public static ApiException invalid(String field, String problem) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed", Map.of(field, problem));
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(ErrorCode.UNAUTHORIZED, message, null);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message, null);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message, null);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message, null);
    }

    public ErrorCode code() {
        return code;
    }

    /** Per-field problems, or {@code null} when the failure is not a validation failure. */
    public Map<String, String> fields() {
        return fields;
    }
}
