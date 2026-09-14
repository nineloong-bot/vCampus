package edu.seu.vcampus.client.shop.commerce;
import edu.seu.vcampus.client.core.network.ClientConnection;
import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
/** Uses the application's authenticated socket, never a separate demo connection. */
public final class SocketCommerceTransport implements CommerceTransport {
    private final ClientConnection connection;
    /** Creates the adapter around the shared session connection. */
    public SocketCommerceTransport(ClientConnection connection) { this.connection = connection; }
    @Override public CompletableFuture<Serializable> execute(String command, Serializable body, String requestId) {
        return CompletableFuture.supplyAsync(() -> connection.<Serializable>send(command,body,Duration.ofSeconds(30),requestId))
                .thenCompose(f -> f).thenApply(r -> {
                    if (!r.success()) throw new CommerceFailure(r.code(),r.message());
                    return r.data();
                });
    }
}
