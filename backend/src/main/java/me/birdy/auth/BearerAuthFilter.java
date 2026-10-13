package me.birdy.auth;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import java.util.Optional;
import me.birdy.api.ApiException;

/** Requires a valid {@code Authorization: Bearer <token>} header on every resource not marked {@link Public}. */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class BearerAuthFilter implements ContainerRequestFilter {

    private static final String SCHEME = "Bearer ";

    @Context
    private ResourceInfo resourceInfo;

    @Inject
    private SessionService sessions;

    @Inject
    private CurrentUser currentUser;

    @Override
    public void filter(ContainerRequestContext request) {
        if (isPublic()) {
            return;
        }
        AuthenticatedUser user = bearerToken(request.getHeaderString(HttpHeaders.AUTHORIZATION))
                .flatMap(sessions::authenticate)
                .orElseThrow(() -> ApiException.unauthorized("A valid bearer token is required"));
        currentUser.set(user);
    }

    private boolean isPublic() {
        return resourceInfo.getResourceMethod().isAnnotationPresent(Public.class)
                || resourceInfo.getResourceClass().isAnnotationPresent(Public.class);
    }

    /** Extracts the token of a bearer {@code Authorization} header; anything that cannot be a token is dropped. */
    static Optional<String> bearerToken(String header) {
        if (header == null || !header.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            return Optional.empty();
        }
        String token = header.substring(SCHEME.length()).strip();
        return token.length() == Tokens.TOKEN_LENGTH ? Optional.of(token) : Optional.empty();
    }
}
