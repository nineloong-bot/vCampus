package edu.seu.vcampus.server.student.handler;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.student.repository.TrainingPlanException;
import edu.seu.vcampus.server.student.service.StudentGradeService;
import edu.seu.vcampus.server.student.service.TrainingPlanService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Registers training plan and grade commands and enforces authorization. */
public final class TrainingPlanHandlers {
    public static final List<String> PLAN_COMMANDS = List.of(
            "TRAINING_PLAN_SAVE", "TRAINING_PLAN_GET", "TRAINING_PLAN_LIST",
            "TRAINING_PLAN_SAVE_COURSE", "TRAINING_PLAN_REMOVE_COURSE",
            "TRAINING_PLAN_IMPORT_COURSES");
    public static final List<String> GRADE_COMMANDS = List.of(
            "GRADE_RECORD", "GRADE_BATCH_RECORD", "GRADE_LIST_BY_STUDENT");
    public static final List<String> STUDENT_COMMANDS = List.of(
            "TRAINING_PLAN_GET_MY", "GRADE_GET_MY");

    private final TrainingPlanService planService;
    private final StudentGradeService gradeService;
    private final StudentAuthorizationPort authorization;
    private final StudentWriteExecutor writes;

    public TrainingPlanHandlers(TrainingPlanService planService,
            StudentGradeService gradeService, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes) {
        this.planService = Objects.requireNonNull(planService);
        this.gradeService = Objects.requireNonNull(gradeService);
        this.authorization = Objects.requireNonNull(authorization);
        this.writes = Objects.requireNonNull(writes);
    }

    public void register(MessageRouter router) {
        router.register("TRAINING_PLAN_SAVE", typed(SaveTrainingPlanCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> planService.savePlan(body, principal(message).userId())))));
        router.register("TRAINING_PLAN_GET", typed(EntityIdRequest.class,
                (message, body) -> admin(message,
                        () -> planService.getPlan(body.entityId()))));
        router.register("TRAINING_PLAN_LIST", typed(TrainingPlanQuery.class,
                (message, body) -> admin(message, () -> planService.searchPlans(body))));
        router.register("TRAINING_PLAN_SAVE_COURSE", typed(SaveTrainingPlanCourseCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> planService.saveCourse(body, principal(message).userId())))));
        router.register("TRAINING_PLAN_REMOVE_COURSE", typed(EntityIdRequest.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> { planService.removeCourse(body.entityId(), principal(message).userId()); return edu.seu.vcampus.common.protocol.EmptyResponse.INSTANCE; }))));
        router.register("TRAINING_PLAN_IMPORT_COURSES", typed(ImportTrainingPlanCoursesCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> new ArrayList<>(planService.importCourses(body, principal(message).userId()))))));
        router.register("TRAINING_PLAN_GET_MY", typed(EmptyRequest.class,
                (message, body) -> student(message,
                        () -> planService.getMyPlan(principal(message).userId()))));
        router.register("GRADE_RECORD", typed(RecordStudentGradeCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> gradeService.recordGrade(body, principal(message).userId())))));
        router.register("GRADE_BATCH_RECORD", typed(BatchRecordGradesCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> new ArrayList<>(gradeService.batchRecordGrades(body, principal(message).userId()))))));
        router.register("GRADE_LIST_BY_STUDENT", typed(EntityIdRequest.class,
                (message, body) -> admin(message,
                        () -> gradeService.getTranscriptByStudentId(body.entityId()))));
        router.register("GRADE_GET_MY", typed(EmptyRequest.class,
                (message, body) -> student(message,
                        () -> gradeService.getMyTranscript(principal(message).userId()))));
    }

    private ResponseBody<? extends Serializable> write(Message message,
            java.util.function.Supplier<ResponseBody<? extends Serializable>> action) {
        StudentPrincipal principal = principal(message);
        String id = java.util.UUID.nameUUIDFromBytes((principal.userId() + "\n"
                + message.command() + "\n" + message.requestId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        Message scoped = new Message(id, message.type(), message.command(),
                message.sessionToken(), message.body(), message.timestamp());
        return writes.execute(scoped, principal, () -> {
            try {
                return action.get();
            } catch (ConcurrentModificationException error) {
                return ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null);
            } catch (TrainingPlanException error) {
                return ResponseBody.failure(error.code(), error.getMessage(), null);
            } catch (IllegalArgumentException | IllegalStateException error) {
                return ResponseBody.failure("COMMON_INVALID_REQUEST", error.getMessage(), null);
            }
        });
    }

    private ResponseBody<? extends Serializable> admin(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        StudentPrincipal p = principal(message);
        return p.hasRole("ADMIN") ? success(action.get()) : forbidden();
    }

    private ResponseBody<? extends Serializable> student(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        StudentPrincipal p = principal(message);
        return p.hasRole("STUDENT") ? success(action.get()) : forbidden();
    }

    private StudentPrincipal principal(Message message) {
        StudentPrincipal p = authorization.authenticate(message.sessionToken());
        if (p == null) throw new IllegalArgumentException("Invalid session");
        return p;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> MessageHandler typed(Class<T> type,
            BiFunction<Message, T, ResponseBody<? extends Serializable>> action) {
        return (message, client) -> {
            if (!type.isInstance(message.body()))
                return ResponseBody.failure("COMMON_INVALID_REQUEST", "请求体类型错误", null);
            try {
                return action.apply(message, type.cast(message.body()));
            } catch (ConcurrentModificationException error) {
                return ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null);
            } catch (TrainingPlanException error) {
                return ResponseBody.failure(error.code(), error.getMessage(), null);
            } catch (edu.seu.vcampus.server.student.service.StudentNotFoundException error) {
                return ResponseBody.failure("STUDENT_NOT_FOUND", "学生不存在", null);
            } catch (IllegalArgumentException | IllegalStateException error) {
                return ResponseBody.failure("COMMON_INVALID_REQUEST", error.getMessage(), null);
            }
        };
    }

    private static <T extends Serializable> ResponseBody<T> success(T value) {
        return ResponseBody.success(value);
    }

    private static ResponseBody<Serializable> forbidden() {
        return ResponseBody.failure("COMMON_FORBIDDEN", "无权访问", null);
    }
}
