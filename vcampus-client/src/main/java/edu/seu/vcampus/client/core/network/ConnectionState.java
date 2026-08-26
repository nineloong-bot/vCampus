package edu.seu.vcampus.client.core.network;

/** Observable lifecycle states for the client-server connection. */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}
