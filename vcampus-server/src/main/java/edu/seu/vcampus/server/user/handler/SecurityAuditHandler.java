package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.ForbiddenException;
import edu.seu.vcampus.server.user.service.SecurityAuditService;

import java.io.Serializable;
import java.util.Objects;

/** Handles the separate administrator-only SECURITY_AUDIT_SEARCH read command. */
public final class SecurityAuditHandler implements MessageHandler {
    private final AuthorizationPort authorization;
    private final SecurityAuditService audits;

    /** Creates the audit handler from existing authorization and query services. */
    public SecurityAuditHandler(
            AuthorizationPort authorization, SecurityAuditService audits) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.audits = Objects.requireNonNull(audits, "audits");
    }

    /** Validates the body, enforces USER_AUDIT_READ, and returns a safe page. */
    @Override
    public ResponseBody<? extends Serializable> handle(
            Message message, ClientContext context) {
        try {
            if (!(message.body() instanceof SecurityAuditQuery query)) {
                throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
            }
            authorization.requirePermission(message.sessionToken(), "USER_AUDIT_READ");
            if (authorization.requireSession(message.sessionToken()).role() != UserRole.ADMIN) {
                throw new ForbiddenException();
            }
            return ResponseBody.success(audits.search(query));
        } catch (RuntimeException error) {
            return UserHandlerErrorMapper.failure(error, "审计记录查询失败");
        }
    }
}
