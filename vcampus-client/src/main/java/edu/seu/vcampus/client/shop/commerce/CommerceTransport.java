package edu.seu.vcampus.client.shop.commerce;
import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
/** Typed-envelope transport; a write retains its key while its outcome is uncertain. */
@FunctionalInterface public interface CommerceTransport {
    /** Sends one authenticated command using an explicit durable request identity. */
    CompletableFuture<Serializable> execute(String command, Serializable body, String requestId);
}
