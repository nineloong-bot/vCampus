package edu.seu.vcampus.server.user.repository;

import java.sql.Connection;

/** Persistence boundary for appending security audit events. */
public interface AuditRepository {
    /** Records an event targeting the acting user in the caller's transaction. */
    default void record(Connection connection, String userId, String actionCode,
                        String resultCode) {
        record(connection, userId, actionCode, "USER", userId, resultCode);
    }

    /** Records a metadata-only security event with an explicit target. */
    void record(Connection connection, String userId, String actionCode,
                String targetType, String targetId, String resultCode);
}
