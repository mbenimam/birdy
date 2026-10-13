package me.birdy.auth;

import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import me.birdy.config.AppConfig;

/** Periodically purges expired sessions, including those whose token is never presented again. */
@ApplicationScoped
public class SessionCleanup {

    private static final Logger LOG = System.getLogger(SessionCleanup.class.getName());

    @Resource
    private ManagedScheduledExecutorService scheduler;

    @Inject
    private SessionService sessions;

    @Inject
    private AppConfig config;

    private ScheduledFuture<?> task;

    void onStartup(@Observes Startup event) {
        long seconds = config.sessionCleanupInterval().toSeconds();
        task = scheduler.scheduleWithFixedDelay(this::purge, seconds, seconds, TimeUnit.SECONDS);
        LOG.log(Level.INFO, "Expired sessions are purged every {0} s", seconds);
    }

    void onShutdown(@Observes Shutdown event) {
        if (task != null) {
            task.cancel(false);
        }
    }

    private void purge() {
        try {
            long removed = sessions.purgeExpired();
            if (removed > 0) {
                LOG.log(Level.INFO, "Purged {0} expired session(s)", removed);
            }
        } catch (RuntimeException e) {
            // Swallowed on purpose: an exception would cancel all future runs of the task.
            LOG.log(Level.WARNING, "Purging expired sessions failed", e);
        }
    }
}
