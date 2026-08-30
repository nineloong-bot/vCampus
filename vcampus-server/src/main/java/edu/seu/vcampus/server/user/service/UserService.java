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

    /** Applies for a teacher account with request metadata used only by audit. */
    default UserView applyForTeacherAccount(
            TeacherAccountApplicationCommand command, ClientContext context) {
        return applyForTeacherAccount(command);
    }

    /** Authenticates an active account for the requesting client connection. */
    LoginResult login(LoginCommand command, ClientContext context);

    /** Revokes the supplied session token; repeated calls are harmless. */
    void logout(String sessionToken);

    /** Revokes a session and records its originating client address when known. */
    default void logout(String sessionToken, ClientContext context) {
        logout(sessionToken);
    }

    /** Returns the current safe account projection for a valid session. */
    UserView getCurrentUser(String sessionToken);

    /** Verifies the old password, stores the replacement, and revokes all user sessions. */
    void changePassword(String sessionToken, ChangePasswordCommand command);

    /** Changes a password with request metadata used only by audit. */
    default void changePassword(String sessionToken, ChangePasswordCommand command,
                                ClientContext context) {
        changePassword(sessionToken, command);
    }

    /** Searches safe account summaries using administrator-controlled filters and paging. */
    PageResult<UserSummary> searchUsers(UserSearchQuery query);

    /** Changes a base role while applying optimistic-version and last-admin protection. */
    UserView updateRole(UpdateUserRoleCommand command);

    /** Changes a role while retaining the authenticated actor for audit. */
    default UserView updateRole(String actorUserId, UpdateUserRoleCommand command,
                                ClientContext context) {
        return updateRole(command);
    }

    /** Changes account lifecycle state while applying optimistic-version and last-admin protection. */
    UserView changeStatus(ChangeUserStatusCommand command);

    /** Changes status while retaining the authenticated actor for audit. */
    default UserView changeStatus(String actorUserId, ChangeUserStatusCommand command,
                                  ClientContext context) {
        return changeStatus(command);
    }

    /**
     * Audits a request rejected before business-service entry in an independent
     * short transaction; implementations must persist only a stable error code.
     */
    default void auditRejectedRequest(String actorUserId, String actionCode,
                                      String targetId, RuntimeException failure,
                                      ClientContext context) {
        // Optional for test doubles; production implementations persist this event.
    }

    /** Revokes all sessions belonging to a user after an account-security change. */
    void revokeSessionsForUser(String userId);
}
