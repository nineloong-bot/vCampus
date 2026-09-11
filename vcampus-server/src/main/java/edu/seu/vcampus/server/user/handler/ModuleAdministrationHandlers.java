package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.governance.AssignModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.RemoveModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.SwapModuleAdministratorsCommand;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.governance.ModuleAdministrationService;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.ForbiddenException;
import edu.seu.vcampus.server.security.UserIdentity;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Registers the four super-administrator module-governance commands. */
public final class ModuleAdministrationHandlers {
    private final ModuleAdministrationService service;
    private final AuthorizationPort authorization;
    private final RequestDeduplicator deduplicator;

    /** Registers governance routes using existing authorization and deduplication services. */
    public ModuleAdministrationHandlers(MessageRouter router,
            ModuleAdministrationService service, AuthorizationPort authorization,
            RequestDeduplicator deduplicator) {
        this.service = Objects.requireNonNull(service, "service");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.deduplicator = deduplicator;
        Objects.requireNonNull(router, "router");
        router.register("PLATFORM_MODULE_ADMIN_LIST", listHandler());
        router.register("PLATFORM_MODULE_ADMIN_ASSIGN", assignHandler());
        router.register("PLATFORM_MODULE_ADMIN_REMOVE", removeHandler());
        router.register("PLATFORM_MODULE_ADMIN_SWAP", swapHandler());
    }

    private MessageHandler listHandler() {
        return (message, context) -> {
            try {
                body(EmptyRequest.class, message.body());
                requireSuperAdministrator(message, "PLATFORM_MODULE_ADMIN_READ");
                return ResponseBody.success(service.list());
            } catch (RuntimeException error) {
                return failure(error, "权限分配查询失败");
            }
        };
    }

    private MessageHandler assignHandler() {
        return (message, context) -> {
            try {
                AssignModuleAdministratorCommand command = body(
                        AssignModuleAdministratorCommand.class, message.body());
                return write(message, context, command,
                        "GOVERNANCE_MODULE_ADMIN_ASSIGN",
                        value -> value.moduleCode(), value -> value.userId(),
                        (actor, value) -> service.assign(actor, value));
            } catch (RuntimeException error) {
                service.auditRejected(null, "GOVERNANCE_MODULE_ADMIN_ASSIGN",
                        null, null, error);
                return failure(error, "权限分配操作失败");
            }
        };
    }

    private MessageHandler removeHandler() {
        return (message, context) -> {
            try {
                RemoveModuleAdministratorCommand command = body(
                        RemoveModuleAdministratorCommand.class, message.body());
                return write(message, context, command,
                        "GOVERNANCE_MODULE_ADMIN_REMOVE",
                        value -> value.moduleCode(), value -> value.userId(),
                        (actor, value) -> service.remove(actor, value));
            } catch (RuntimeException error) {
                service.auditRejected(null, "GOVERNANCE_MODULE_ADMIN_REMOVE",
                        null, null, error);
                return failure(error, "权限分配操作失败");
            }
        };
    }

    private MessageHandler swapHandler() {
        return (message, context) -> {
            try {
                SwapModuleAdministratorsCommand command = body(
                        SwapModuleAdministratorsCommand.class, message.body());
                return write(message, context, command,
                        "GOVERNANCE_MODULE_ADMIN_SWAP",
                        value -> value.firstModuleCode(), value -> value.firstUserId(),
                        (actor, value) -> service.swap(actor, value));
            } catch (RuntimeException error) {
                service.auditRejected(null, "GOVERNANCE_MODULE_ADMIN_SWAP",
                        null, null, error);
                return failure(error, "权限分配操作失败");
            }
        };
    }

    private <T extends Serializable> ResponseBody<? extends Serializable> write(
            Message message, ClientContext context, T command, String actionCode,
            java.util.function.Function<T, String> module,
            java.util.function.Function<T, String> target,
            GovernanceAction<T> action) {
        String actor = null;
        AtomicBoolean entered = new AtomicBoolean();
        try {
            authorization.requirePermission(message.sessionToken(),
                    "PLATFORM_MODULE_ADMIN_WRITE");
            UserIdentity identity = authorization.requireSession(message.sessionToken());
            requireSuperAdministrator(identity);
            actor = identity.userId();
            String authenticatedActor = actor;
            java.util.function.Supplier<ResponseBody<EmptyResponse>> operation = () -> {
                entered.set(true);
                action.run(authenticatedActor, command);
                return ResponseBody.success(EmptyResponse.INSTANCE);
            };
            return deduplicator == null ? operation.get()
                    : deduplicator.executeOnce(message, actor, context.connectionId(), operation);
        } catch (RuntimeException error) {
            if (!entered.get()) {
                service.auditRejected(actor, actionCode, module.apply(command),
                        target.apply(command), error);
            }
            return failure(error, "权限分配操作失败");
        }
    }

    private UserIdentity requireSuperAdministrator(Message message, String permission) {
        authorization.requirePermission(message.sessionToken(), permission);
        UserIdentity identity = authorization.requireSession(message.sessionToken());
        requireSuperAdministrator(identity);
        return identity;
    }

    private static void requireSuperAdministrator(UserIdentity identity) {
        if (identity.role() != UserRole.SUPER_ADMIN) throw new ForbiddenException();
    }

    private static <T extends Serializable> T body(Class<T> type, Serializable value) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
        return type.cast(value);
    }

    private static <T extends Serializable> ResponseBody<T> failure(
            RuntimeException error, String message) {
        return UserHandlerErrorMapper.failure(error, message);
    }

    @FunctionalInterface
    private interface GovernanceAction<T> {
        void run(String actorUserId, T command);
    }
}
