package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.user.service.UserService;

import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Registers the eight public user-module socket commands and their safe response mapping. */
public final class UserHandlers {
    private static final Set<String> STABLE_CODES = Set.of("AUTH_SESSION_EXPIRED",
            "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED", "AUTH_FORBIDDEN",
            "AUTH_PASSWORD_POLICY_VIOLATION", "USER_LOGIN_ID_EXISTS", "USER_LAST_ADMIN_PROTECTED",
            "USER_ROLE_CONFLICT", "USER_STATUS_CONFLICT", "USER_NOT_FOUND");
    private final UserService users;
    private final AuthorizationPort authorization;

    /** Registers all and only the public user commands on the supplied router. */
    public UserHandlers(MessageRouter router, UserService users, AuthorizationPort authorization) {
        this.users = Objects.requireNonNull(users, "users");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        Objects.requireNonNull(router, "router");
        router.register("USER_REGISTER", publicHandler(TeacherAccountApplicationCommand.class,
                users::applyForTeacherAccount));
        router.register("USER_LOGIN", new UserLoginHandler(users));
        router.register("USER_LOGOUT", sessionHandler(EmptyResponse.class, ignored -> {
            users.logout(ignored.token());
            return EmptyResponse.INSTANCE;
        }));
        router.register("USER_GET_CURRENT", sessionHandler(EmptyResponse.class,
                ignored -> users.getCurrentUser(ignored.token())));
        router.register("USER_CHANGE_PASSWORD", sessionHandler(ChangePasswordCommand.class,
                command -> { users.changePassword(command.token(), command.body()); return EmptyResponse.INSTANCE; }));
        router.register("USER_SEARCH", permissionHandler("USER_READ_ALL", UserSearchQuery.class,
                users::searchUsers));
        router.register("USER_UPDATE_ROLE", permissionHandler("USER_ROLE_WRITE",
                UpdateUserRoleCommand.class, users::updateRole));
        router.register("USER_CHANGE_STATUS", permissionHandler("USER_STATUS_WRITE",
                ChangeUserStatusCommand.class, users::changeStatus));
    }

    private <T extends Serializable, R extends Serializable> MessageHandler publicHandler(
            Class<T> type, Function<T, R> operation) {
        return (message, context) -> execute(() -> operation.apply(type.cast(message.body())));
    }

    private <T extends Serializable, R extends Serializable> MessageHandler sessionHandler(
            Class<T> type, Function<SessionCommand<T>, R> operation) {
        return (message, context) -> execute(() -> {
            authorization.requireSession(message.sessionToken());
            return operation.apply(new SessionCommand<>(message.sessionToken(), type.cast(message.body())));
        });
    }

    private <T extends Serializable, R extends Serializable> MessageHandler permissionHandler(
            String permission, Class<T> type, Function<T, R> operation) {
        return (message, context) -> execute(() -> {
            authorization.requirePermission(message.sessionToken(), permission);
            return operation.apply(type.cast(message.body()));
        });
    }

    private static <T extends Serializable> ResponseBody<T> execute(java.util.function.Supplier<T> operation) {
        try {
            return ResponseBody.success(operation.get());
        } catch (RuntimeException error) {
            return ResponseBody.failure(code(error), "请求未能完成", null);
        }
    }

    private static String code(RuntimeException error) {
        if (error instanceof ConcurrentModificationException) return "USER_CONCURRENT_MODIFICATION";
        return STABLE_CODES.contains(error.getMessage()) ? error.getMessage() : "COMMON_OPERATION_FAILED";
    }

    private record SessionCommand<T>(String token, T body) {
    }
}
