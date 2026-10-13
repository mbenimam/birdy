package me.birdy.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Activates Jakarta REST under {@code /api}; resources and providers are discovered by scanning. */
@ApplicationPath("/api")
public class RestApplication extends Application {
}
