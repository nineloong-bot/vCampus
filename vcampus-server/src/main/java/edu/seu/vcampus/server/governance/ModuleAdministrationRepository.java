package edu.seu.vcampus.server.governance;

import edu.seu.vcampus.common.governance.ModuleAdministratorView;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;

import java.sql.Connection;
import java.util.List;

/** Persistence boundary for dedicated module-administrator accounts. */
interface ModuleAdministrationRepository {
    List<ModuleAdministratorView> list(Connection connection);

    AdministratorAccount requireAccount(
            Connection connection, String userId, long expectedVersion);

    long countActive(Connection connection, UserRole role);

    void updateRoleAndStatus(Connection connection, String userId, UserRole role,
                             AccountStatus status, long expectedVersion);

    record AdministratorAccount(
            String userId, String loginId, UserRole role,
            AccountStatus status, long rowVersion) { }
}
