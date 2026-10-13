package me.birdy.api;

import java.util.Locale;

/** The error codes of the API contract with their HTTP status. */
public enum ErrorCode {
    VALIDATION_FAILED(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    INTERNAL_ERROR(500);

    private final int status;

    ErrorCode(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** The code as it appears in the JSON body, for example {@code validation_failed}. */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Picks the code that describes an arbitrary HTTP error status best. */
    public static ErrorCode forStatus(int status) {
        for (ErrorCode code : values()) {
            if (code.status == status) {
                return code;
            }
        }
        return status >= 500 ? INTERNAL_ERROR : VALIDATION_FAILED;
    }
}
