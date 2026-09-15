package edu.seu.vcampus.server.student.majortransfer.handler;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.security.InitialPasswordChangeRequiredException;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.student.handler.StudentAuthorizationPort;
import edu.seu.vcampus.server.student.handler.StudentPrincipal;
import edu.seu.vcampus.server.student.handler.StudentWriteExecutor;
import edu.seu.vcampus.server.student.majortransfer.security.MajorTransferCollegeAuthorizationService;
import edu.seu.vcampus.server.student.majortransfer.service.MajorTransferException;
import edu.seu.vcampus.server.student.majortransfer.service.MajorTransferService;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ConcurrentModificationException;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Provides major transfer handler support behavior. */
final class MajorTransferHandlerSupport {
    final MajorTransferService service;
    final MajorTransferCollegeAuthorizationService scope;
    private final StudentAuthorizationPort authorization;
    private final StudentWriteExecutor writes;

    MajorTransferHandlerSupport(MajorTransferService service,
            StudentAuthorizationPort authorization, StudentWriteExecutor writes,
            MajorTransferCollegeAuthorizationService scope) {
        this.service = service;
        this.authorization = authorization;
        this.writes = writes;
        this.scope = scope;
    }

    StudentPrincipal principal(Message message) {
        StudentPrincipal principal = authorization.authenticate(message.sessionToken());
        if (principal == null) throw new SessionExpiredException();
        return principal;
    }

    ResponseBody<? extends Serializable> studentRead(Message message,
            Supplier<? extends Serializable> action) {
        return principal(message).hasRole("STUDENT") ? success(action.get()) : forbidden();
    }

    ResponseBody<? extends Serializable> batchRead(Message message,
            Supplier<? extends Serializable> action) {
        StudentPrincipal principal = principal(message);
        return principal.hasRole("STUDENT_ADMIN") || principal.hasRole("COLLEGE_ADMIN")
                ? success(action.get()) : forbidden();
    }

    ResponseBody<? extends Serializable> collegeRead(Message message, Runnable authorization,
            Supplier<? extends Serializable> action) {
        if (!principal(message).hasRole("COLLEGE_ADMIN") || !allowed(authorization)) {
            return forbidden();
        }
        return success(action.get());
    }

    ResponseBody<? extends Serializable> centralWrite(Message message,
            Supplier<? extends Serializable> action) {
        return write(message, "STUDENT_ADMIN", null, ignored -> action.get());
    }

    ResponseBody<? extends Serializable> studentWrite(Message message,
            Supplier<? extends Serializable> action) {
        return write(message, "STUDENT", null, ignored -> action.get());
    }

    ResponseBody<? extends Serializable> collegeWrite(Message message, Runnable authorization,
            Function<String, ? extends Serializable> action) {
        return write(message, "COLLEGE_ADMIN", authorization, action);
    }

    private ResponseBody<? extends Serializable> write(Message message, String role,
            Runnable authorization, Function<String, ? extends Serializable> action) {
        StudentPrincipal principal = principal(message);
        if (!principal.hasRole(role) || !allowed(authorization)) return forbidden();
        String departmentId = null;
        if ("COLLEGE_ADMIN".equals(role)) {
            try {
                departmentId = scope.findActiveDepartmentId(principal.userId());
            } catch (IllegalArgumentException error) {
                if ("COMMON_FORBIDDEN".equals(error.getMessage())) return forbidden();
                throw error;
            }
        }
        String replayId = UUID.nameUUIDFromBytes((principal.userId() + "\n"
                + message.command() + "\n" + message.requestId())
                .getBytes(StandardCharsets.UTF_8)).toString();
        Message scoped = new Message(replayId, message.type(), message.command(),
                message.sessionToken(), message.body(), message.timestamp());
        String trustedDepartmentId = departmentId;
        return writes.execute(scoped, principal, () -> invoke(
                () -> action.apply(trustedDepartmentId)));
    }

    private static ResponseBody<? extends Serializable> invoke(
            Supplier<? extends Serializable> action) {
        try {
            return success(action.get());
        } catch (ConcurrentModificationException error) {
            return ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION",
                    "数据已被修改，请刷新", null);
        } catch (MajorTransferException error) {
            return ResponseBody.failure(error.code(), error.getMessage(), null);
        } catch (IllegalArgumentException | IllegalStateException error) {
            return ResponseBody.failure("COMMON_INVALID_REQUEST", error.getMessage(), null);
        }
    }

    static boolean allowed(Runnable authorization) {
        if (authorization == null) return true;
        try {
            authorization.run();
            return true;
        } catch (IllegalArgumentException error) {
            if ("COMMON_FORBIDDEN".equals(error.getMessage())) return false;
            throw error;
        }
    }

    static <T extends Serializable> MessageHandler typed(Class<T> type,
            BiFunction<Message, T, ResponseBody<? extends Serializable>> action) {
        return (message, client) -> {
            if (!type.isInstance(message.body())) {
                return ResponseBody.failure("COMMON_INVALID_REQUEST", "请求体类型错误", null);
            }
            try {
                return action.apply(message, type.cast(message.body()));
            } catch (ConcurrentModificationException error) {
                return ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION",
                        "数据已被修改，请刷新", null);
            } catch (MajorTransferException error) {
                return ResponseBody.failure(error.code(), error.getMessage(), null);
            } catch (SessionExpiredException error) {
                return ResponseBody.failure(error.getMessage(), "会话已过期，请重新登录", null);
            } catch (InitialPasswordChangeRequiredException error) {
                return ResponseBody.failure(error.getMessage(), "请先修改初始密码", null);
            } catch (IllegalArgumentException error) {
                return ResponseBody.failure("COMMON_INVALID_REQUEST", error.getMessage(), null);
            }
        };
    }

    static <T extends Serializable> ResponseBody<T> success(T value) {
        return ResponseBody.success(value);
    }

    static ResponseBody<Serializable> forbidden() {
        return ResponseBody.failure("COMMON_FORBIDDEN", "无权访问", null);
    }
}
