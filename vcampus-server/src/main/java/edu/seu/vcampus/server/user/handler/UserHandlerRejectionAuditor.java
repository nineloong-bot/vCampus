package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.user.service.UserService;

import java.io.Serializable;
import java.util.Objects;

/** Writes sanitized audits only for requests rejected before business-service entry. */
final class UserHandlerRejectionAuditor {
    private final UserService users;

    UserHandlerRejectionAuditor(UserService users) {
        this.users = Objects.requireNonNull(users, "users");
    }

    <T extends Serializable> ResponseBody<T> reject(
            String actorUserId, String actionCode, String targetId,
            RuntimeException error, ClientContext context, String safeMessage) {
        try {
            users.auditRejectedRequest(actorUserId, actionCode, targetId, error, context);
        } catch (RuntimeException ignored) {
            // Audit persistence must never replace the original safe client response.
        }
        return UserHandlerErrorMapper.failure(error, safeMessage);
    }
}
