package edu.seu.vcampus.common.protocol;

import java.io.Serial;
import java.io.Serializable;

/** Immutable envelope exchanged by the VCampus client and server. */
/**
 * Carries immutable message data.
 * @param requestId the request identifier
 * @param type the type
 * @param command the command
 * @param sessionToken the session token
 * @param body the body
 * @param timestamp the timestamp
 */
public record Message(
        String requestId,
        MessageType type,
        String command,
        String sessionToken,
        Serializable body,
        long timestamp) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
