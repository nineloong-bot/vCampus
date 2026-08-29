package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
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
import java.util.Objects;
import java.util.function.Function;

/** Registers the eight public user-module socket commands and their safe response mapping. */
public final class UserHandlers {
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
        router.register("USER_LOGOUT", logoutHandler());
        router.register("USER_GET_CURRENT", sessionHandler(EmptyRequest.class,
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
        return (message, context) -> execute(
                () -> operation.apply(requireBody(type, message.body())));
    }

    private MessageHandler logoutHandler() {
        return (message, context) -> execute(() -> {
            requireBody(EmptyRequest.class, message.body());
            users.logout(message.sessionToken());
            return EmptyResponse.INSTANCE;
        });
    }

    private <T extends Serializable, R extends Serializable> MessageHandler sessionHandler(
            Class<T> type, Function<SessionCommand<T>, R> operation) {
        return (message, context) -> execute(() -> {
            T body = requireBody(type, message.body());
            authorization.requireSession(message.sessionToken());
            return operation.apply(new SessionCommand<>(message.sessionToken(), body));
        });
    }

    private <T extends Serializable, R extends Serializable> MessageHandler permissionHandler(
            String permission, Class<T> type, Function<T, R> operation) {
        return (message, context) -> execute(() -> {
            T body = requireBody(type, message.body());
            authorization.requirePermission(message.sessionToken(), permission);
            return operation.apply(body);
        });
    }

    private static <T extends Serializable> ResponseBody<T> execute(
            java.util.function.Supplier<T> operation) {
        try {
            return ResponseBody.success(operation.get());
        } catch (RuntimeException error) {
            return UserHandlerErrorMapper.failure(error, "请求未能完成");
        }
    }

    private static <T extends Serializable> T requireBody(Class<T> type, Serializable body) {
        if (!type.isInstance(body)) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
        return type.cast(body);
    }

    private record SessionCommand<T>(String token, T body) {
    }
}
