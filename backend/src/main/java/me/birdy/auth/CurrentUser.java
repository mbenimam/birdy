package me.birdy.auth;

import jakarta.enterprise.context.RequestScoped;

/** Holds the caller of the current request; filled in by {@link BearerAuthFilter}. */
@RequestScoped
public class CurrentUser {

    private AuthenticatedUser user;

    void set(AuthenticatedUser user) {
        this.user = user;
    }

    public AuthenticatedUser get() {
        if (user == null) {
            throw new IllegalStateException("No authenticated user in this request");
        }
        return user;
    }

    public long id() {
        return get().id();
    }

    public String username() {
        return get().username();
    }
}
