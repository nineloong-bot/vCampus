package edu.seu.vcampus.server.routing;

/** Identifies the connection that submitted a message. */
public record ClientContext(String connectionId, String clientAddress) {
}
