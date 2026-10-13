package me.birdy.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException exception) {
        ErrorCode code = exception.code();
        return ErrorResponses.of(code.status(), code, exception.getMessage(), exception.fields());
    }
}
