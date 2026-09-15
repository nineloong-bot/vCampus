package edu.seu.vcampus.server.student.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.security.InitialPasswordChangeRequiredException;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.student.numbering.StudentNumberingException;
import edu.seu.vcampus.server.student.service.StudentAdmissionService;
import edu.seu.vcampus.server.student.service.StudentAdmissionException;
import edu.seu.vcampus.server.student.service.StudentNotFoundException;
import edu.seu.vcampus.server.student.service.StudentOrganizationQuery;
import edu.seu.vcampus.server.student.service.StudentProfileApplicationException;
import edu.seu.vcampus.server.student.service.StudentService;
import edu.seu.vcampus.server.student.service.StudentProfileService;
import edu.seu.vcampus.server.student.pdf.StudentProfilePdfGenerator;
import edu.seu.vcampus.server.student.repository.OrganizationHierarchyException;
import edu.seu.vcampus.server.student.security.StudentCollegeScopeAuthorizationService;

import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Registers the ten student commands and enforces their authorization boundary. */
public final class StudentHandlers {
    public static final List<String> COMMANDS = List.of("STUDENT_CREATE", "STUDENT_CREATE_MANUAL", "STUDENT_BATCH_IMPORT",
            "STUDENT_GET_CURRENT",
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

    private final StudentService students;
    private final StudentAuthorizationPort authorization;
    private final StudentAcademicAdministrationHandlers academicHandlers;
    private final StudentRecordHandlers recordHandlers;
    private final StudentProfileHandlers profileHandlers;

    StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization) {
        this(admissions, students, organizations, authorization,
                (request, principal, action) -> action.get(), null, null, null);
    }

    /**
     * Creates a student handlers with its required collaborators.
     * @param admissions the admissions
     * @param students the students
     * @param organizations the organizations
     * @param authorization the authorization
     * @param writes the writes
     */
    public StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes) {
        this(admissions, students, organizations, authorization, writes, null, null, null);
    }

    /**
     * Creates a student handlers with its required collaborators.
     * @param admissions the admissions
     * @param students the students
     * @param organizations the organizations
     * @param authorization the authorization
     * @param writes the writes
     * @param profiles the profiles
     */
    public StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes, StudentProfileService profiles) {
        this(admissions, students, organizations, authorization, writes, profiles, null, null);
    }

    /**
     * Creates a student handlers with its required collaborators.
     * @param admissions the admissions
     * @param students the students
     * @param organizations the organizations
     * @param authorization the authorization
     * @param writes the writes
     * @param profiles the profiles
     * @param pdfs the pdfs
     */
    public StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes, StudentProfileService profiles,
            StudentProfilePdfGenerator pdfs) {
        this(admissions, students, organizations, authorization, writes, profiles, pdfs, null);
    }

    /** Creates handlers with server-resolved college scope enforcement. */
    public StudentHandlers(StudentAdmissionService admissions, StudentService students,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes, StudentProfileService profiles,
            StudentProfilePdfGenerator pdfs,
            StudentCollegeScopeAuthorizationService collegeScope) {
        this.students = Objects.requireNonNull(students);
        this.authorization = Objects.requireNonNull(authorization);
        this.academicHandlers = new StudentAcademicAdministrationHandlers(admissions,
                organizations, authorization, writes, collegeScope);
        this.recordHandlers = new StudentRecordHandlers(students, authorization, writes,
                collegeScope);
        this.profileHandlers = profiles == null ? null
                : new StudentProfileHandlers(profiles, pdfs, authorization, writes, collegeScope);
    }

    /**
     * Performs the register operation.
     * @param router the router
     */
    public void register(MessageRouter router) {
        academicHandlers.register(router);
        router.register("STUDENT_GET_CURRENT", typed(EmptyRequest.class, (message, body) -> {
            StudentPrincipal principal = principal(message);
            if (!principal.hasRole("STUDENT") && !principal.hasRole("ADMIN")) return forbidden();
            return success(students.getCurrentStudent(principal.userId()));
        }));
        recordHandlers.register(router);
        if (profileHandlers != null) profileHandlers.register(router);
    }

    private StudentPrincipal principal(Message message) {
        StudentPrincipal principal = authorization.authenticate(message.sessionToken());
        if (principal == null) throw new SessionExpiredException();
        return principal;
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
            } catch (SessionExpiredException error) {
                return ResponseBody.failure(error.getMessage(), "会话已过期，请重新登录", null);
            } catch (InitialPasswordChangeRequiredException error) {
                return ResponseBody.failure(error.getMessage(), "请先修改初始密码", null);
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
