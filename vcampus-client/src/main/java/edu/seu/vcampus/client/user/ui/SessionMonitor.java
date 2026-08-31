package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.user.service.SessionExpiredClientException;
import edu.seu.vcampus.client.user.service.UserClientService;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Polls the existing current-user command while one authenticated shell is active. */
final class SessionMonitor {
    private static final int CHECK_INTERVAL_MILLIS = 2_000;
    private final UserClientService users;
    private final Runnable onSessionExpired;
    private final Timer timer = new Timer(CHECK_INTERVAL_MILLIS, event -> check());
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean expiryDelivered = new AtomicBoolean();
    private boolean active;

    SessionMonitor(UserClientService users, Runnable onSessionExpired) {
        this.users = Objects.requireNonNull(users, "users");
        this.onSessionExpired = Objects.requireNonNull(onSessionExpired, "onSessionExpired");
        timer.setCoalesce(true);
    }

    void start() {
        requireEdt();
        if (active) return;
        active = true;
        timer.start();
    }

    void stop() {
        requireEdt();
        active = false;
        timer.stop();
    }

    private void check() {
        requireEdt();
        if (!active || !inFlight.compareAndSet(false, true)) return;
        CompletableFuture<?> response;
        try {
            response = users.getCurrentUser();
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        if (response == null) response = CompletableFuture.failedFuture(
                new IllegalStateException("Session check returned no result"));
        response.whenComplete((ignored, failure) -> onEdt(() -> finish(failure)));
    }

    private void finish(Throwable failure) {
        inFlight.set(false);
        if (!active || !isSessionExpired(failure)) return;
        stop();
        if (expiryDelivered.compareAndSet(false, true)) onSessionExpired.run();
    }

    private static boolean isSessionExpired(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof SessionExpiredClientException;
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Session monitor must run on the EDT");
        }
    }
}
