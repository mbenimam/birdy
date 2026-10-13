package me.birdy.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

/** Makes the system UTC clock injectable, so time-dependent beans can be tested with a fixed clock. */
@ApplicationScoped
public class ClockProducer {

    @Produces
    public Clock clock() {
        return Clock.systemUTC();
    }
}
