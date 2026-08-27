package edu.seu.vcampus.server.user.repository;

import java.sql.Connection;

/** Persistence boundary for appending security audit events. */
public interface AuditRepository {
    /** Records a user-targeted security event in the caller's transaction. */
    void record(Connection connection, String userId, String actionCode,
                String resultCode);
}
