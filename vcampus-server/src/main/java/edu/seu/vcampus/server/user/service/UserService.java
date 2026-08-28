package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.routing.ClientContext;

/** Application boundary for user-management use cases implemented so far. */
public interface UserService {
    /** Applies for a pending teacher account using a public registration command. */
    UserView applyForTeacherAccount(TeacherAccountApplicationCommand command);

    /** Authenticates an active account for the requesting client connection. */
    LoginResult login(LoginCommand command, ClientContext context);

    /** Revokes the supplied session token; repeated calls are harmless. */
    void logout(String sessionToken);

    /** Returns the current safe account projection for a valid session. */
    UserView getCurrentUser(String sessionToken);

    /** Verifies the old password, stores the replacement, and revokes all user sessions. */
    void changePassword(String sessionToken, ChangePasswordCommand command);

    /** Revokes all sessions belonging to a user after an account-security change. */
    void revokeSessionsForUser(String userId);
}
