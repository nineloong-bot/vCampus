package edu.seu.vcampus.server.student.governance;

import edu.seu.vcampus.common.error.ErrorDetail;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.governance.AssignStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.DeactivateStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.TransferStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.ForbiddenException;
import edu.seu.vcampus.server.security.UserIdentity;

import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Registers college-administrator governance commands for the student administrator. */
public final class StudentCollegeAdministrationHandlers {
    private static final Set<String> STABLE_CODES = Set.of(
            "AUTH_FORBIDDEN", "AUTH_SESSION_EXPIRED",
            "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED",
            "COMMON_VALIDATION_FAILED", "STUDENT_LAST_COLLEGE_ADMIN_PROTECTED");

    private final StudentCollegeAdministrationService service;
    private final AuthorizationPort authorization;
    private final RequestDeduplicator deduplicator;

    /** Registers search, assignment, transfer and deactivation routes. */
    public StudentCollegeAdministrationHandlers(MessageRouter router,
            StudentCollegeAdministrationService service,
            AuthorizationPort authorization, RequestDeduplicator deduplicator) {
        this.service = Objects.requireNonNull(service);
        this.authorization = Objects.requireNonNull(authorization);
        this.deduplicator = deduplicator;
        Objects.requireNonNull(router).register("STUDENT_COLLEGE_ADMIN_SEARCH", search());
        router.register("STUDENT_COLLEGE_ADMIN_ASSIGN", assign());
        router.register("STUDENT_COLLEGE_ADMIN_TRANSFER", transfer());
        router.register("STUDENT_COLLEGE_ADMIN_DEACTIVATE", deactivate());
    }

    private edu.seu.vcampus.server.routing.MessageHandler search() {
        return (message, context) -> {
            try {
                body(EmptyRequest.class, message.body());
                requireStudentAdministrator(message, "STUDENT_COLLEGE_ADMIN_READ");
                return ResponseBody.success(service.list());
            } catch (RuntimeException error) {
                return failure(error, "学院管理员查询失败");
            }
        };
    }

    private edu.seu.vcampus.server.routing.MessageHandler assign() {
        return (message, context) -> handleWrite(message, context,
                AssignStudentCollegeAdministratorCommand.class,
                service::assign);
    }

    private edu.seu.vcampus.server.routing.MessageHandler transfer() {
        return (message, context) -> handleWrite(message, context,
                TransferStudentCollegeAdministratorCommand.class,
                service::transfer);
    }

    private edu.seu.vcampus.server.routing.MessageHandler deactivate() {
        return (message, context) -> handleWrite(message, context,
                DeactivateStudentCollegeAdministratorCommand.class,
                service::deactivate);
    }

    private <T extends Serializable> ResponseBody<? extends Serializable> handleWrite(
            Message message, ClientContext context, Class<T> type,
            BiConsumer<String, T> operation) {
        try {
            T command = body(type, message.body());
            UserIdentity actor = requireStudentAdministrator(message,
                    "STUDENT_COLLEGE_ADMIN_WRITE");
            java.util.function.Supplier<ResponseBody<EmptyResponse>> action = () -> {
                operation.accept(actor.userId(), command);
                return ResponseBody.success(EmptyResponse.INSTANCE);
            };
            return deduplicator == null ? action.get() : deduplicator.executeOnce(
                    message, actor.userId(), context.connectionId(), action);
        } catch (RuntimeException error) {
            return failure(error, "学院管理员维护失败");
        }
    }

    private UserIdentity requireStudentAdministrator(Message message, String permission) {
        authorization.requirePermission(message.sessionToken(), permission);
        UserIdentity identity = authorization.requireSession(message.sessionToken());
        if (identity.role() != UserRole.STUDENT_ADMIN) throw new ForbiddenException();
        return identity;
    }

    private static <T extends Serializable> T body(Class<T> type, Serializable value) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
        return type.cast(value);
    }

    private static <T extends Serializable> ResponseBody<T> failure(
            RuntimeException error, String safeMessage) {
        String code = code(error);
        ErrorDetail detail = "COMMON_INTERNAL_ERROR".equals(code)
                ? new ErrorDetail(code, safeMessage, Map.of(),
                        UUID.randomUUID().toString(), false) : null;
        return ResponseBody.failure(code, safeMessage, detail);
    }

    private static String code(RuntimeException error) {
        if (error instanceof ConcurrentModificationException) {
            return "COMMON_CONCURRENT_MODIFICATION";
        }
        if (error instanceof IllegalArgumentException) {
            return "COMMON_VALIDATION_FAILED";
        }
        String message = error.getMessage();
        return message != null && STABLE_CODES.contains(message)
                ? message : "COMMON_INTERNAL_ERROR";
    }
}
