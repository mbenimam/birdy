package me.birdy.api;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Map;

/** Gives the errors raised by Jakarta REST itself (unknown path, wrong method, unreadable body) the contract body. */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static final Logger LOG = System.getLogger(WebApplicationExceptionMapper.class.getName());

    @Override
    public Response toResponse(WebApplicationException exception) {
        int status = exception.getResponse().getStatus();
        if (status < 400) {
            return exception.getResponse();
        }
        if (status >= 500) {
            LOG.log(Level.ERROR, "Request failed", exception);
            return ErrorResponses.of(status, ErrorCode.INTERNAL_ERROR, "Internal server error", null);
        }
        ErrorCode code = ErrorCode.forStatus(status);
        Map<String, String> fields = status == ErrorCode.VALIDATION_FAILED.status() ? Map.of() : null;
        return ErrorResponses.of(status, code, message(status), fields);
    }

    private static String message(int status) {
        Response.Status known = Response.Status.fromStatusCode(status);
        return known == null ? "Request failed" : known.getReasonPhrase();
    }
}
