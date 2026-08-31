package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.common.paging.PageResult;

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

    /** Searches safe account summaries using administrator-controlled filters and paging. */
    PageResult<UserSummary> searchUsers(UserSearchQuery query);

    /** Changes a base role while applying optimistic-version and last-admin protection. */
    UserView updateRole(UpdateUserRoleCommand command);

    /** Changes a base role and attributes its audit event to the authenticated actor. */
    default UserView updateRole(String actorId, UpdateUserRoleCommand command) {
        return updateRole(command);
    }

    /** Changes account lifecycle state while applying optimistic-version and last-admin protection. */
    UserView changeStatus(ChangeUserStatusCommand command);

    /** Changes lifecycle state and attributes its audit event to the authenticated actor. */
    default UserView changeStatus(String actorId, ChangeUserStatusCommand command) {
        return changeStatus(command);
    }

    /** Revokes all sessions belonging to a user after an account-security change. */
    void revokeSessionsForUser(String userId);
}
