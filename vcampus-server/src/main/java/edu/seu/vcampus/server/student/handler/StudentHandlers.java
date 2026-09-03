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
import edu.seu.vcampus.server.student.service.StudentProfileApplicationException;
import edu.seu.vcampus.server.student.service.StudentProfileService;
import edu.seu.vcampus.server.student.numbering.StudentNumberingException;
import edu.seu.vcampus.server.student.repository.OrganizationHierarchyException;
import edu.seu.vcampus.server.student.pdf.StudentProfilePdfGenerator;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.ConcurrentModificationException;
import java.util.function.BiFunction;

/** Registers the ten student commands and enforces their authorization boundary. */
public final class StudentHandlers {
    public static final List<String> COMMANDS = List.of("STUDENT_CREATE", "STUDENT_CREATE_MANUAL", "STUDENT_GET_CURRENT",
            "STUDENT_GET", "STUDENT_SEARCH", "STUDENT_UPDATE_CONTACT",
            "STUDENT_UPDATE_ENROLLMENT", "STUDENT_CHANGE_STATUS", "STUDENT_UPDATE_INFO",
            "STUDENT_UPDATE_ACADEMIC",
            "STUDENT_LIST_DEPARTMENTS",
            "STUDENT_LIST_MAJORS", "STUDENT_LIST_CLASSES", "STUDENT_GET_CHANGES",
            "STUDENT_SAVE_DEPARTMENT", "STUDENT_SAVE_MAJOR", "STUDENT_SAVE_CLASS");
    public static final List<String> PROFILE_COMMANDS = List.of(
            "STUDENT_PROFILE_GET_WORKSPACE", "STUDENT_PROFILE_SAVE_PERSONAL_DRAFT",
            "STUDENT_PROFILE_SAVE_ATTENDANCE_DRAFT", "STUDENT_PROFILE_SUBMIT", "STUDENT_PROFILE_WITHDRAW",
            "STUDENT_PROFILE_EXPORT_PDF",
            "STUDENT_PROFILE_REVIEW_LIST", "STUDENT_PROFILE_REVIEW_GET",
            "STUDENT_PROFILE_APPROVE", "STUDENT_PROFILE_REJECT",
            "STUDENT_GET_PROFILE");

    private final StudentAdmissionService admissions;
    private final StudentService students;
    private final StudentOrganizationQuery organizations;
    private final StudentAuthorizationPort authorization;
    private final StudentWriteExecutor writes;
    private final StudentProfileService profiles;
    private final StudentProfilePdfGenerator pdfs;

    StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization) {
        this(admissions, students, organizations, authorization,
                (request, principal, action) -> action.get(), null);
    }

    public StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes) {
        this(admissions, students, organizations, authorization, writes, null, null);
    }

    public StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes, StudentProfileService profiles) {
        this(admissions, students, organizations, authorization, writes, profiles, null);
    }

    public StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes, StudentProfileService profiles,
            StudentProfilePdfGenerator pdfs) {
        this.admissions = Objects.requireNonNull(admissions);
        this.students = Objects.requireNonNull(students);
        this.organizations = Objects.requireNonNull(organizations);
        this.authorization = Objects.requireNonNull(authorization);
        this.writes = Objects.requireNonNull(writes);
        this.profiles = profiles;
        this.pdfs = pdfs;
    }

    public void register(MessageRouter router) {
        router.register("STUDENT_CREATE", typed(CreateStudentAdmissionCommand.class, (message, body) -> {
            StudentPrincipal principal = principal(message);
            if (!principal.hasPermission("STUDENT_WRITE")) return forbidden();
            return success(admissions.admit(body, context(message, principal)));
        }));
        router.register("STUDENT_CREATE_MANUAL", typed(CreateStudentManualCommand.class,
                (message, body) -> strictAdmin(message, () -> admissions.createManual(
                        body, context(message, principal(message))))));
        router.register("STUDENT_GET_CURRENT", typed(EmptyRequest.class, (message, body) -> {
            StudentPrincipal principal = principal(message);
            if (!principal.hasRole("STUDENT") && !principal.hasRole("ADMIN")) return forbidden();
            return success(students.getCurrentStudent(principal.userId()));
        }));
        router.register("STUDENT_GET", typed(EntityIdRequest.class, (message, body) -> {
            StudentPrincipal principal = principal(message);
            if (!isStaff(principal)) return forbidden();
            return success(students.getStudent(body.entityId()));
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
        router.register("STUDENT_UPDATE_INFO", typed(UpdateStudentInfoCommand.class,
                (message, body) -> write(message,
                        () -> admin(message, () -> students.updateStudentInfo(body,
                                principal(message).userId())))));
        router.register("STUDENT_UPDATE_ACADEMIC", typed(UpdateStudentAcademicCommand.class,
                (message, body) -> write(message,
                        () -> admin(message, () -> students.updateStudentAcademic(body,
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
        if (profiles != null) registerProfiles(router);
    }

    private void registerProfiles(MessageRouter router) {
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
                        java.time.Instant.now()))));
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
                (message, body) -> strictAdmin(message,
                        () -> profiles.getProfileByStudentId(body.entityId()))));
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

    private ResponseBody<? extends Serializable> student(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        StudentPrincipal principal = principal(message);
        return principal.hasRole("STUDENT") ? success(action.get()) : forbidden();
    }

    private ResponseBody<? extends Serializable> strictAdmin(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        StudentPrincipal principal = principal(message);
        return principal.hasRole("ADMIN") ? success(action.get()) : forbidden();
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
            } catch (StudentProfileApplicationException error) {
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
