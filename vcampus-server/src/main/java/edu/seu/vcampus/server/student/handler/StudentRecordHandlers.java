package edu.seu.vcampus.server.student.handler;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.student.security.StudentCollegeScopeAuthorizationService;
import edu.seu.vcampus.server.student.service.StudentAdmissionException;
import edu.seu.vcampus.server.student.service.StudentNotFoundException;
import edu.seu.vcampus.server.student.service.StudentService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.function.BiFunction;

/** Registers college-scoped reads and mutations for concrete student records. */
public final class StudentRecordHandlers {
    private final StudentService students;
    private final StudentAuthorizationPort authorization;
    private final StudentWriteExecutor writes;
    private final StudentCollegeScopeAuthorizationService collegeScope;

    /** Creates record handlers whose college scope is resolved from live server state. */
    public StudentRecordHandlers(StudentService students,
            StudentAuthorizationPort authorization, StudentWriteExecutor writes,
            StudentCollegeScopeAuthorizationService collegeScope) {
        this.students = Objects.requireNonNull(students);
        this.authorization = Objects.requireNonNull(authorization);
        this.writes = Objects.requireNonNull(writes);
        this.collegeScope = collegeScope;
    }

    /** Registers concrete student record routes. */
    public void register(MessageRouter router) {
        router.register("STUDENT_GET", typed(EntityIdRequest.class, (message, body) -> {
            StudentPrincipal actor = principal(message);
            if (!staff(actor) && !actor.hasRole("COLLEGE_ADMIN")) return forbidden();
            String departmentId = actor.hasRole("COLLEGE_ADMIN") ? department(actor) : null;
            if (actor.hasRole("COLLEGE_ADMIN") && departmentId == null) return forbidden();
            StudentView value = students.getStudent(body.entityId(), departmentId);
            return success(actor.hasRole("TEACHER") ? withoutContact(value) : value);
        }));
        router.register("STUDENT_SEARCH", typed(StudentSearchQuery.class, (message, body) -> {
            StudentPrincipal actor = principal(message);
            if (staff(actor)) return success(students.searchStudents(body));
            String departmentId = actor.hasRole("COLLEGE_ADMIN") ? department(actor) : null;
            return departmentId == null ? forbidden()
                    : success(students.searchStudents(body, departmentId));
        }));
        router.register("STUDENT_UPDATE_CONTACT", typed(UpdateStudentContactCommand.class,
                (message, body) -> write(message, () -> updateContact(message, body))));
        router.register("STUDENT_UPDATE_ENROLLMENT", typed(UpdateStudentEnrollmentCommand.class,
                (message, body) -> write(message, () -> scoped(message, departmentId ->
                        students.updateEnrollment(body, principal(message).userId(), departmentId)))));
        router.register("STUDENT_CHANGE_STATUS", typed(ChangeStudentStatusCommand.class,
                (message, body) -> write(message, () -> scoped(message, departmentId ->
                        students.changeStatus(body, principal(message).userId(), departmentId)))));
        router.register("STUDENT_UPDATE_INFO", typed(UpdateStudentInfoCommand.class,
                (message, body) -> write(message, () -> scoped(message, departmentId ->
                        students.updateStudentInfo(body, principal(message).userId(), departmentId)))));
        router.register("STUDENT_UPDATE_ACADEMIC", typed(UpdateStudentAcademicCommand.class,
                (message, body) -> write(message, () -> scoped(message, departmentId ->
                        students.updateStudentAcademic(body, principal(message).userId(), departmentId)))));
        router.register("STUDENT_GET_CHANGES", typed(EntityIdRequest.class,
                (message, body) -> getChanges(message, body.entityId())));
    }

    private ResponseBody<? extends Serializable> getChanges(Message message, String studentId) {
        StudentPrincipal actor = principal(message);
        if (actor.hasRole("STUDENT")) {
            try {
                StudentView student = students.getStudent(studentId);
                if (student != null && actor.userId().equals(student.userId())) {
                    return success(new ArrayList<>(students.listChanges(studentId, null)));
                }
            } catch (Exception ignored) {
            }
            return forbidden();
        }
        return scoped(message, departmentId ->
                new ArrayList<>(students.listChanges(studentId, departmentId)));
    }

    private ResponseBody<? extends Serializable> updateContact(Message message,
            UpdateStudentContactCommand body) {
        StudentPrincipal actor = principal(message);
        StudentView student = students.getStudent(body.studentId());
        if (actor.userId().equals(student.userId()) || isSystemAdmin(actor))
            return success(students.updateContact(body));
        String departmentId = actor.hasRole("COLLEGE_ADMIN") ? department(actor) : null;
        return departmentId == null ? forbidden()
                : success(students.updateContact(body, departmentId));
    }

    private ResponseBody<? extends Serializable> scoped(Message message,
            java.util.function.Function<String, ? extends Serializable> action) {
        StudentPrincipal actor = principal(message);
        if (isSystemAdmin(actor)) return success(action.apply(null));
        String departmentId = actor.hasRole("COLLEGE_ADMIN") ? department(actor) : null;
        return departmentId == null ? forbidden() : success(action.apply(departmentId));
    }

    private ResponseBody<? extends Serializable> write(Message message,
            java.util.function.Supplier<ResponseBody<? extends Serializable>> action) {
        return writes.execute(message, principal(message), action);
    }

    private String department(StudentPrincipal actor) {
        if (collegeScope == null) return null;
        try { return collegeScope.requireActiveDepartment(actor.userId()); }
        catch (IllegalArgumentException error) { return null; }
    }

    private StudentPrincipal principal(Message message) {
        StudentPrincipal actor = authorization.authenticate(message.sessionToken());
        if (actor == null) throw new IllegalArgumentException("Invalid session");
        return actor;
    }

    private static boolean isSystemAdmin(StudentPrincipal actor) {
        return actor.hasRole("ADMIN") || actor.hasRole("SUPER_ADMIN");
    }

    private static boolean staff(StudentPrincipal actor) {
        return actor.hasRole("TEACHER") || isSystemAdmin(actor);
    }

    private static StudentView withoutContact(StudentView value) {
        return new StudentView(value.studentId(), value.userId(), value.campusCardNumber(),
                value.studentNumber(), value.studentType(), value.studentName(), value.gender(),
                null, null, value.majorId(), value.classId(), value.enrollmentDate(),
                value.status(), value.rowVersion(), value.departmentName(), value.majorName(),
                value.className());
    }

    private static <T extends Serializable> MessageHandler typed(Class<T> type,
            BiFunction<Message, T, ResponseBody<? extends Serializable>> action) {
        return (message, client) -> {
            if (!type.isInstance(message.body())) return ResponseBody.failure(
                    "COMMON_INVALID_REQUEST", "请求体类型错误", null);
            try { return action.apply(message, type.cast(message.body())); }
            catch (ConcurrentModificationException error) { return ResponseBody.failure(
                    "COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null); }
            catch (StudentNotFoundException error) { return ResponseBody.failure(
                    "STUDENT_NOT_FOUND", "学生不存在", null); }
            catch (StudentAdmissionException error) { return ResponseBody.failure(
                    error.code(), error.getMessage(), null); }
            catch (IllegalArgumentException error) {
                return "COMMON_FORBIDDEN".equals(error.getMessage()) ? forbidden()
                        : ResponseBody.failure("COMMON_INVALID_REQUEST", error.getMessage(), null);
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
