package edu.seu.vcampus.server.student.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestContext;
import edu.seu.vcampus.server.student.service.StudentAdmissionService;
import edu.seu.vcampus.server.student.service.StudentOrganizationQuery;
import edu.seu.vcampus.server.student.service.StudentService;
import edu.seu.vcampus.server.student.service.StudentAdmissionException;
import edu.seu.vcampus.server.student.service.StudentNotFoundException;
import edu.seu.vcampus.server.student.numbering.StudentNumberingException;
import edu.seu.vcampus.server.student.repository.OrganizationHierarchyException;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.ConcurrentModificationException;
import java.util.function.BiFunction;

/** Registers the ten student commands and enforces their authorization boundary. */
public final class StudentHandlers {
    public static final List<String> COMMANDS = List.of("STUDENT_CREATE", "STUDENT_GET_CURRENT",
            "STUDENT_GET", "STUDENT_SEARCH", "STUDENT_UPDATE_CONTACT",
            "STUDENT_UPDATE_ENROLLMENT", "STUDENT_CHANGE_STATUS", "STUDENT_LIST_DEPARTMENTS",
            "STUDENT_LIST_MAJORS", "STUDENT_LIST_CLASSES", "STUDENT_GET_CHANGES",
            "STUDENT_SAVE_DEPARTMENT", "STUDENT_SAVE_MAJOR", "STUDENT_SAVE_CLASS");

    private final StudentAdmissionService admissions;
    private final StudentService students;
    private final StudentOrganizationQuery organizations;
    private final StudentAuthorizationPort authorization;
    private final StudentWriteExecutor writes;

    StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization) {
        this(admissions, students, organizations, authorization,
                (request, principal, action) -> action.get());
    }

    public StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes) {
        this.admissions = Objects.requireNonNull(admissions);
        this.students = Objects.requireNonNull(students);
        this.organizations = Objects.requireNonNull(organizations);
        this.authorization = Objects.requireNonNull(authorization);
        this.writes = Objects.requireNonNull(writes);
    }

    public void register(MessageRouter router) {
        router.register("STUDENT_CREATE", typed(CreateStudentAdmissionCommand.class, (message, body) -> {
            StudentPrincipal principal = principal(message);
            if (!principal.hasPermission("STUDENT_WRITE")) return forbidden();
            return success(admissions.admit(body, context(message, principal)));
        }));
        router.register("STUDENT_GET_CURRENT", typed(EmptyRequest.class, (message, body) -> {
            StudentPrincipal principal = principal(message);
            if (!principal.hasRole("STUDENT") && !principal.hasRole("ADMIN")) return forbidden();
            return success(students.getCurrentStudent(principal.userId()));
        }));
        router.register("STUDENT_GET", typed(EntityIdRequest.class, (message, body) -> {
            StudentPrincipal principal = principal(message);
            if (!isStaff(principal)) return forbidden();
            StudentView value = students.getStudent(body.entityId());
            return success(principal.hasRole("TEACHER") ? withoutContact(value) : value);
        }));
        router.register("STUDENT_SEARCH", typed(StudentSearchQuery.class, (message, body) -> {
            StudentPrincipal principal = principal(message);
            return isStaff(principal) ? success(students.searchStudents(body)) : forbidden();
        }));
        router.register("STUDENT_UPDATE_CONTACT", typed(UpdateStudentContactCommand.class,
                (message, body) -> write(message, () -> updateContact(message, body))));
        router.register("STUDENT_UPDATE_ENROLLMENT", typed(UpdateStudentEnrollmentCommand.class,
                (message, body) -> write(message,
                        () -> admin(message, () -> students.updateEnrollment(body,
                                principal(message).userId())))));
        router.register("STUDENT_CHANGE_STATUS", typed(ChangeStudentStatusCommand.class,
                (message, body) -> write(message,
                        () -> admin(message, () -> students.changeStatus(body,
                                principal(message).userId())))));
        router.register("STUDENT_LIST_DEPARTMENTS", typed(ActiveOnlyQuery.class,
                (message, body) -> authenticated(message,
                        () -> new ArrayList<>(organizations.listDepartments(body.activeOnly())))));
        router.register("STUDENT_LIST_MAJORS", typed(OrganizationChildrenQuery.class,
                (message, body) -> authenticated(message,
                        () -> new ArrayList<>(organizations.listMajors(body.parentId(), body.activeOnly())))));
        router.register("STUDENT_LIST_CLASSES", typed(OrganizationChildrenQuery.class,
                (message, body) -> authenticated(message,
                        () -> new ArrayList<>(organizations.listClasses(body.parentId(), body.activeOnly())))));
        router.register("STUDENT_GET_CHANGES", typed(EntityIdRequest.class, (message, body) -> {
            StudentPrincipal principal = principal(message);
            return isStaff(principal) ? success(new ArrayList<>(students.listChanges(body.entityId()))) : forbidden();
        }));
        router.register("STUDENT_SAVE_DEPARTMENT", typed(SaveDepartmentCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> organizations.saveDepartment(body)))));
        router.register("STUDENT_SAVE_MAJOR", typed(SaveMajorCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> organizations.saveMajor(body)))));
        router.register("STUDENT_SAVE_CLASS", typed(SaveClassCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> organizations.saveClass(body)))));
    }

    private ResponseBody<? extends Serializable> updateContact(Message message,
            UpdateStudentContactCommand body) {
        StudentPrincipal principal = principal(message);
        var student = students.getStudent(body.studentId());
        return principal.hasRole("ADMIN") || principal.userId().equals(student.userId())
                ? success(students.updateContact(body)) : forbidden();
    }

    private ResponseBody<? extends Serializable> write(Message message,
            java.util.function.Supplier<ResponseBody<? extends Serializable>> action) {
        return writes.execute(message, principal(message), action);
    }

    private ResponseBody<? extends Serializable> admin(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        StudentPrincipal principal = principal(message);
        return principal.hasRole("ADMIN") || principal.hasPermission("STUDENT_WRITE")
                ? success(action.get()) : forbidden();
    }

    private ResponseBody<? extends Serializable> authenticated(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        principal(message);
        return success(action.get());
    }

    private StudentPrincipal principal(Message message) {
        StudentPrincipal principal = authorization.authenticate(message.sessionToken());
        if (principal == null) throw new IllegalArgumentException("Invalid session");
        return principal;
    }

    private static boolean isStaff(StudentPrincipal principal) {
        return principal.hasRole("TEACHER") || principal.hasRole("ADMIN");
    }

    private static StudentView withoutContact(StudentView value) {
        return new StudentView(value.studentId(), value.userId(), value.campusCardNumber(),
                value.studentNumber(), value.studentType(), value.studentName(), value.gender(),
                null, null, value.majorId(), value.classId(), value.enrollmentDate(),
                value.status(), value.rowVersion());
    }

    private static RequestContext context(Message message, StudentPrincipal principal) {
        return new RequestContext(message.requestId(), principal.userId(), "socket");
    }

    private static <T extends Serializable> MessageHandler typed(Class<T> type,
            BiFunction<Message, T, ResponseBody<? extends Serializable>> action) {
        return (message, client) -> {
            if (!type.isInstance(message.body()))
                return ResponseBody.failure("COMMON_INVALID_REQUEST", "请求体类型错误", null);
            try {
                return action.apply(message, type.cast(message.body()));
            } catch (ConcurrentModificationException error) {
                return ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null);
            } catch (StudentNotFoundException error) {
                return ResponseBody.failure("STUDENT_NOT_FOUND", "学生不存在", null);
            } catch (StudentAdmissionException error) {
                return ResponseBody.failure(error.code(), error.getMessage(), null);
            } catch (StudentNumberingException error) {
                return ResponseBody.failure(error.code(), error.getMessage(), null);
            } catch (OrganizationHierarchyException error) {
                return ResponseBody.failure("STUDENT_ORGANIZATION_HAS_ACTIVE_CHILDREN", error.getMessage(), null);
            } catch (UnsupportedOperationException error) {
                return ResponseBody.failure("COMMON_NOT_SUPPORTED", error.getMessage(), null);
            } catch (IllegalArgumentException error) {
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
