package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.user.domain.UserAccount;

/** Maps private account persistence data to safe public account projections. */
final class UserViews {
    private UserViews() { }

    static UserView from(UserAccount account) {
        return new UserView(account.userId(), account.loginId(), account.role(), account.accountStatus(),
                account.mustChangePassword(), account.lastLoginAt(), account.rowVersion(),
                account.createdAt(), account.updatedAt());
    }

    static UserIdentity identity(UserAccount account) {
        return new UserIdentity(account.userId(), account.loginId(), account.role(),
                account.accountStatus());
    }
}
