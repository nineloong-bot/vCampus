package edu.seu.vcampus.server.routing;

/** Authenticated request identity propagated into coordinated writes. */
public record RequestContext(String requestId, String userId, String clientInstanceId) {
    /**
     * Creates a request context with its required collaborators.
     * @param requestId the request identifier
     * @param userId the user identifier
     * @param clientInstanceId the client instance identifier
     */
    public RequestContext {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
        clientInstanceId = clientInstanceId == null ? "unknown" : clientInstanceId;
    }
}
