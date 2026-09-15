package edu.seu.vcampus.client.shop.commerce;
import edu.seu.vcampus.client.core.network.ClientConnection;
import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
/** Uses the application's authenticated socket, never a separate demo connection. */
public final class SocketCommerceTransport implements CommerceTransport {
    private final ClientConnection connection;
    private final Runnable sessionExpired;
    private final java.util.concurrent.atomic.AtomicBoolean loginRequested = new java.util.concurrent.atomic.AtomicBoolean();
    /** Creates the adapter around the shared session connection. */
    public SocketCommerceTransport(ClientConnection connection) { this(connection, () -> { }); }
    /** Keeps the application shell's login handoff for expired sessions. */
    public SocketCommerceTransport(ClientConnection connection, Runnable sessionExpired) {
        this.connection = connection;
        this.sessionExpired = java.util.Objects.requireNonNull(sessionExpired);
    }
    @Override public CompletableFuture<Serializable> execute(String command, Serializable body, String requestId) {
        return CompletableFuture.supplyAsync(() -> connection.<Serializable>send(command,body,Duration.ofSeconds(30),requestId))
                .thenCompose(f -> f).thenApply(r -> {
                    if (!r.success()) {
                        if ("AUTH_SESSION_EXPIRED".equals(r.code()) && loginRequested.compareAndSet(false, true)) {
                            javax.swing.SwingUtilities.invokeLater(sessionExpired);
                        }
                        throw new CommerceFailure(r.code(),r.message());
                    }
                    return r.data();
                });
    }
}
