package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.ResetStudentPasswordCommand;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.ForbiddenException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.user.service.UserService;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;

/** Registers the compatible user-module socket commands and their safe response mapping. */
public final class UserHandlers {
    private final UserService users;
    private final AuthorizationPort authorization;
    private final RequestDeduplicator deduplicator;
    private final UserHandlerRejectionAuditor rejectionAuditor;

    /** Registers handlers without persistence deduplication, primarily for focused tests. */
    public UserHandlers(MessageRouter router, UserService users,
                        AuthorizationPort authorization) {
        this(router, users, authorization, null);
    }

    /** Registers user commands and routes security-sensitive writes through deduplication. */
    public UserHandlers(MessageRouter router, UserService users,
            AuthorizationPort authorization, RequestDeduplicator deduplicator) {
        this.users = Objects.requireNonNull(users, "users");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.deduplicator = deduplicator;
        rejectionAuditor = new UserHandlerRejectionAuditor(users);
        Objects.requireNonNull(router, "router");
        router.register("USER_REGISTER", registrationHandler());
        router.register("USER_LOGIN", new UserLoginHandler(users));
        router.register("USER_LOGOUT", logoutHandler());
        router.register("USER_GET_CURRENT", currentUserHandler());
        router.register("USER_CHANGE_PASSWORD", passwordChangeHandler());
        router.register("USER_SEARCH", searchHandler());
        router.register("USER_UPDATE_ROLE", roleUpdateHandler());
        router.register("USER_CHANGE_STATUS", statusChangeHandler());
        router.register("USER_RESET_STUDENT_PASSWORD", studentPasswordResetHandler());
    }

    private MessageHandler registrationHandler() {
        return (message, context) -> {
            TeacherAccountApplicationCommand command = null;
            try {
                command = requireBody(TeacherAccountApplicationCommand.class, message.body());
                TeacherAccountApplicationCommand request = command;
                return safely(() -> deduplicate(message, context, null,
                        () -> ResponseBody.success(
                                users.applyForTeacherAccount(request, context))));
            } catch (RuntimeException error) {
                String target = command == null ? null : command.loginId();
                return rejected(null, "USER_REGISTER", target, error, context);
            } finally {
                if (command != null) command.clearPassword();
            }
        };
    }
    private MessageHandler logoutHandler() {
        return (message, context) -> {
            try {
                requireBody(EmptyRequest.class, message.body());
            } catch (RuntimeException error) {
                return rejected(null, "USER_LOGOUT", null, error, context);
            }
            try {
                return deduplicate(message, context, null, () -> {
                    // Security-first limitation: the service revokes the in-memory token
                    // before its success audit. An audit failure may therefore return a
                    // failure response even though the token is already revoked.
                    users.logout(message.sessionToken(), context);
                    return ResponseBody.success(EmptyResponse.INSTANCE);
                });
            } catch (RuntimeException error) {
                return rejected(null, "USER_LOGOUT", null, error, context);
            }
        };
    }
    private MessageHandler currentUserHandler() {
        return (message, context) -> {
            try {
                requireBody(EmptyRequest.class, message.body());
                authorization.requireSession(message.sessionToken());
            } catch (RuntimeException error) {
                return rejected(null, "USER_GET_CURRENT", null, error, context);
            }
            return safely(() -> ResponseBody.success(
                    users.getCurrentUser(message.sessionToken())));
        };
    }
    private MessageHandler passwordChangeHandler() {
        return (message, context) -> {
            ChangePasswordCommand command = null;
            try {
                command = requireBody(ChangePasswordCommand.class, message.body());
                UserIdentity actor = authorization.requireSession(message.sessionToken());
                ChangePasswordCommand request = command;
                return protectedWrite(message, context, actor.userId(),
                        "USER_CHANGE_PASSWORD", actor.userId(), () -> {
                    users.changePassword(message.sessionToken(), request, context);
                    return ResponseBody.success(EmptyResponse.INSTANCE);
                });
            } catch (RuntimeException error) {
                return rejected(null, "USER_CHANGE_PASSWORD", null, error, context);
            } finally {
                if (command != null) command.clearPasswords();
            }
        };
    }
    private MessageHandler searchHandler() {
        return (message, context) -> {
            UserSearchQuery query;
            try {
                query = requireBody(UserSearchQuery.class, message.body());
                authorization.requirePermission(message.sessionToken(), "USER_READ_ALL");
            } catch (RuntimeException error) {
                return rejected(null, "USER_SEARCH", null, error, context);
            }
            return safely(() -> ResponseBody.success(users.searchUsers(query)));
        };
    }
    private MessageHandler roleUpdateHandler() {
        return (message, context) -> {
            UpdateUserRoleCommand command = null;
            try {
                command = requireBody(UpdateUserRoleCommand.class, message.body());
                // Compatibility route only: runtime role changes are permanently retired.
                // Do not authenticate, deduplicate, mutate data, or revoke sessions.
                return rejected(null, "USER_UPDATE_ROLE", command.userId(),
                        new IllegalArgumentException("COMMON_VALIDATION_FAILED"), context);
            } catch (RuntimeException error) {
                String target = command == null ? null : command.userId();
                return rejected(null, "USER_UPDATE_ROLE", target, error, context);
            }
        };
    }

    private MessageHandler studentPasswordResetHandler() {
        return (message, context) -> {
            ResetStudentPasswordCommand command = null;
            String actorUserId = null;
            try {
                command = requireBody(ResetStudentPasswordCommand.class, message.body());
                authorization.requirePermission(message.sessionToken(), "USER_PASSWORD_RESET");
                UserIdentity actor = authorization.requireSession(message.sessionToken());
                actorUserId = actor.userId();
                if (actor.role() != edu.seu.vcampus.common.user.UserRole.ADMIN) {
                    throw new ForbiddenException();
                }
                ResetStudentPasswordCommand request = command;
                return protectedWrite(message, context, actorUserId,
                        "USER_PASSWORD_RESET", request.targetUserId(),
                        () -> ResponseBody.success(users.resetStudentPassword(
                                actor.userId(), request, context)));
            } catch (RuntimeException error) {
                String target = command == null ? null : command.targetUserId();
                return rejected(actorUserId, "USER_PASSWORD_RESET", target, error, context);
            }
        };
    }

    private MessageHandler statusChangeHandler() {
        return (message, context) -> {
            ChangeUserStatusCommand command = null;
            try {
                command = requireBody(ChangeUserStatusCommand.class, message.body());
                authorization.requirePermission(message.sessionToken(), "USER_STATUS_WRITE");
                UserIdentity actor = authorization.requireSession(message.sessionToken());
                ChangeUserStatusCommand request = command;
                return protectedWrite(message, context, actor.userId(), "USER_CHANGE_STATUS",
                        request.userId(), () -> ResponseBody.success(users.changeStatus(
                                actor.userId(), request, context)));
            } catch (RuntimeException error) {
                String target = command == null ? null : command.userId();
                return rejected(null, "USER_CHANGE_STATUS", target, error, context);
            }
        };
    }

    private <T extends Serializable> ResponseBody<T> deduplicate(
            Message message, ClientContext context, String actorUserId,
            Supplier<ResponseBody<T>> action) {
        if (deduplicator == null) return safely(action);
        // Foundation persistence is keyed primarily by requestId and is not strongly
        // bound to a user. Protected handlers therefore authenticate this request
        // before entering deduplication. connectionId remains diagnostic fallback only.
        // Password change is intentionally authentication-first even on replay: success
        // revokes the old token, so retrying with it may return AUTH_SESSION_EXPIRED rather
        // than cached success. This prevents an invalid token from receiving a response.
        // Foundation also has no recovery for PROCESSING rows left by claim/complete
        // infrastructure failures; this user-module integration does not claim otherwise.
        return deduplicator.executeOnce(message, actorUserId, context.connectionId(),
                () -> safely(action));
    }
    private <T extends Serializable> ResponseBody<T> protectedWrite(
            Message message, ClientContext context, String actorUserId,
            String actionCode, String targetId, Supplier<ResponseBody<T>> action) {
        try {
            return deduplicate(message, context, actorUserId, action);
        } catch (RuntimeException error) {
            return rejected(actorUserId, actionCode, targetId, error, context);
        }
    }

    private <T extends Serializable> ResponseBody<T> rejected(
            String actorUserId, String actionCode, String targetId,
            RuntimeException error, ClientContext context) {
        return rejectionAuditor.reject(actorUserId, actionCode, targetId, error,
                context, "请求未能完成");
    }

    private static <T extends Serializable> ResponseBody<T> safely(
            Supplier<ResponseBody<T>> operation) {
        try {
            return operation.get();
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    private static <T extends Serializable> ResponseBody<T> failure(RuntimeException error) {
        return UserHandlerErrorMapper.failure(error, "请求未能完成");
    }

    private static <T extends Serializable> T requireBody(Class<T> type, Serializable body) {
        if (!type.isInstance(body)) throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        return type.cast(body);
    }
}
