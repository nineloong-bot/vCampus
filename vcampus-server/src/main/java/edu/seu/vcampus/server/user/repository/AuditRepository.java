package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;

import java.sql.Connection;

/** Persistence boundary for appending security audit events. */
public interface AuditRepository {
    /** Records a self-targeting event in the caller's transaction. */
    default void record(Connection connection, String actorUserId, String actionCode,
                        String resultCode) {
        record(connection, actorUserId, actionCode, "USER", actorUserId,
                resultCode, null);
    }

    /** Records a metadata-only security event with an explicit target. */
    default void record(Connection connection, String actorUserId, String actionCode,
                        String targetType, String targetId, String resultCode) {
        record(connection, actorUserId, actionCode, targetType, targetId,
                resultCode, null);
    }

    /** Records actor, target, stable result, and optional client address. */
    void record(Connection connection, String actorUserId, String actionCode,
                String targetType, String targetId, String resultCode,
                String clientAddress);

    /** Searches sanitized audit rows in fixed newest-first order. */
    default PageResult<SecurityAuditView> search(
            Connection connection, SecurityAuditQuery query) {
        throw new UnsupportedOperationException("Audit search is not available");
    }
}
