package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.user.service.SessionExpiredClientException;
import edu.seu.vcampus.client.user.service.PasswordResetSessionClientException;
import edu.seu.vcampus.client.user.service.UserClientService;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Polls the existing current-user command while one authenticated shell is active. */
final class SessionMonitor {
    private static final int CHECK_INTERVAL_MILLIS = 2_000;
    private final UserClientService users;
    private final Consumer<InvalidationReason> onSessionInvalidated;
    private final Timer timer = new Timer(CHECK_INTERVAL_MILLIS, event -> check());
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean expiryDelivered = new AtomicBoolean();
    private boolean active;

    SessionMonitor(UserClientService users,
                   Consumer<InvalidationReason> onSessionInvalidated) {
        this.users = Objects.requireNonNull(users, "users");
        this.onSessionInvalidated = Objects.requireNonNull(
                onSessionInvalidated, "onSessionInvalidated");
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
        InvalidationReason reason = invalidationReason(failure);
        if (!active || reason == null) return;
        stop();
        if (expiryDelivered.compareAndSet(false, true)) onSessionInvalidated.accept(reason);
    }

    private static InvalidationReason invalidationReason(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof PasswordResetSessionClientException) {
            return InvalidationReason.PASSWORD_RESET;
        }
        if (current instanceof SessionExpiredClientException) {
            return InvalidationReason.REPLACED;
        }
        return null;
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

    enum InvalidationReason {
        REPLACED,
        PASSWORD_RESET
    }
}
