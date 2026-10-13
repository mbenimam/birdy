package me.birdy.api;

import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;

/**
 * Last resort for exceptions no other mapper claims. Two causes get a precise answer: a unique or foreign key
 * violation (two requests raced past the application-level check) and an unparsable JSON body. Everything else
 * is logged and answered with an opaque {@code 500}.
 */
@Provider
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = System.getLogger(UnhandledExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {
        if (hasCause(exception, SQLIntegrityConstraintViolationException.class)) {
            return ErrorResponses.of(ErrorCode.CONFLICT, "The request conflicts with existing data");
        }
        if (hasCause(exception, JsonbException.class)) {
            return ErrorResponses.validation("Malformed JSON body", Map.of());
        }
        LOG.log(Level.ERROR, "Unhandled exception", exception);
        return ErrorResponses.of(ErrorCode.INTERNAL_ERROR, "Internal server error");
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
