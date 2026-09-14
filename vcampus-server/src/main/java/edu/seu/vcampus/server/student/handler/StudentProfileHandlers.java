package edu.seu.vcampus.server.student.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.student.pdf.StudentProfilePdfGenerator;
import edu.seu.vcampus.server.student.service.StudentProfileApplicationException;
import edu.seu.vcampus.server.student.service.StudentProfileService;

import java.io.Serializable;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.function.BiFunction;

/** Registers student self-service profile routes and profile review routes. */
public final class StudentProfileHandlers {
    private final StudentProfileService profiles;
    private final StudentProfilePdfGenerator pdfs;
    private final StudentAuthorizationPort authorization;
    private final StudentWriteExecutor writes;

    /** Creates profile handlers. */
    public StudentProfileHandlers(StudentProfileService profiles,
            StudentProfilePdfGenerator pdfs, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes) {
        this.profiles = Objects.requireNonNull(profiles);
        this.pdfs = pdfs;
        this.authorization = Objects.requireNonNull(authorization);
        this.writes = Objects.requireNonNull(writes);
    }

    /** Registers profile routes. */
    public void register(MessageRouter router) {
        router.register("STUDENT_PROFILE_GET_WORKSPACE", typed(EmptyRequest.class,
                (message, body) -> student(message,
                        () -> profiles.getWorkspace(principal(message).userId()))));
        router.register("STUDENT_PROFILE_SAVE_PERSONAL_DRAFT",
                typed(SaveStudentPersonalDraftCommand.class, (message, body) -> write(message,
                        () -> student(message, () -> profiles.savePersonalDraft(
                                principal(message).userId(), body)))));
        router.register("STUDENT_PROFILE_SAVE_ATTENDANCE_DRAFT",
                typed(SaveStudentAttendanceDraftCommand.class, (message, body) -> write(message,
                        () -> student(message, () -> profiles.saveAttendanceDraft(
                                principal(message).userId(), body)))));
        router.register("STUDENT_PROFILE_SUBMIT", typed(SubmitStudentProfileCommand.class,
                (message, body) -> write(message, () -> student(message,
                        () -> profiles.submit(principal(message).userId(), body)))));
        router.register("STUDENT_PROFILE_WITHDRAW", typed(WithdrawStudentProfileCommand.class,
                (message, body) -> write(message, () -> student(message,
                        () -> profiles.withdraw(principal(message).userId(), body)))));
        if (pdfs != null) router.register("STUDENT_PROFILE_EXPORT_PDF", typed(EmptyRequest.class,
                (message, body) -> student(message, () -> pdfs.generate(
                        profiles.getWorkspace(principal(message).userId()).formalProfile(),
                        Instant.now()))));
        router.register("STUDENT_PROFILE_REVIEW_LIST", typed(StudentProfileReviewQuery.class,
                (message, body) -> admin(message, () -> profiles.listPending(body))));
        router.register("STUDENT_PROFILE_REVIEW_GET", typed(EntityIdRequest.class,
                (message, body) -> admin(message,
                        () -> profiles.getApplication(body.entityId()))));
        router.register("STUDENT_PROFILE_APPROVE", typed(ReviewStudentProfileCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> profiles.approve(body.applicationId(), principal(message).userId(),
                                body.reviewComment())))));
        router.register("STUDENT_PROFILE_REJECT", typed(ReviewStudentProfileCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> profiles.reject(body.applicationId(), principal(message).userId(),
                                body.reviewComment())))));
        router.register("STUDENT_GET_PROFILE", typed(EntityIdRequest.class,
                (message, body) -> admin(message,
                        () -> profiles.getProfileByStudentId(body.entityId()))));
    }

    private ResponseBody<? extends Serializable> write(Message message,
            java.util.function.Supplier<ResponseBody<? extends Serializable>> action) {
        return writes.execute(message, principal(message), action);
    }

    private ResponseBody<? extends Serializable> student(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        return principal(message).hasRole("STUDENT") ? success(action.get()) : forbidden();
    }

    private ResponseBody<? extends Serializable> admin(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        StudentPrincipal actor = principal(message);
        return actor.hasRole("ADMIN") || actor.hasRole("STUDENT_ADMIN")
                ? success(action.get()) : forbidden();
    }

    private StudentPrincipal principal(Message message) {
        StudentPrincipal actor = authorization.authenticate(message.sessionToken());
        if (actor == null) throw new IllegalArgumentException("Invalid session");
        return actor;
    }

    private static <T extends Serializable> MessageHandler typed(Class<T> type,
            BiFunction<Message, T, ResponseBody<? extends Serializable>> action) {
        return (message, client) -> {
            if (!type.isInstance(message.body())) return ResponseBody.failure(
                    "COMMON_INVALID_REQUEST", "请求体类型错误", null);
            try { return action.apply(message, type.cast(message.body())); }
            catch (ConcurrentModificationException error) { return ResponseBody.failure(
                    "COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null); }
            catch (StudentProfileApplicationException error) { return ResponseBody.failure(
                    error.code(), error.getMessage(), null); }
            catch (IllegalArgumentException error) { return ResponseBody.failure(
                    "COMMON_INVALID_REQUEST", error.getMessage(), null); }
        };
    }

    private static <T extends Serializable> ResponseBody<T> success(T value) {
        return ResponseBody.success(value);
    }

    private static ResponseBody<Serializable> forbidden() {
        return ResponseBody.failure("COMMON_FORBIDDEN", "无权访问", null);
    }
}
