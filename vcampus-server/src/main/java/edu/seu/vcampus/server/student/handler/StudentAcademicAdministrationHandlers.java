package edu.seu.vcampus.server.student.handler;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestContext;
import edu.seu.vcampus.server.student.numbering.StudentNumberingException;
import edu.seu.vcampus.server.student.repository.OrganizationHierarchyException;
import edu.seu.vcampus.server.student.security.StudentCollegeScopeAuthorizationService;
import edu.seu.vcampus.server.student.service.StudentAdmissionException;
import edu.seu.vcampus.server.student.service.StudentAdmissionService;
import edu.seu.vcampus.server.student.service.StudentOrganizationQuery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.function.BiFunction;

/** Registers college-scoped admission and academic-organization administration routes. */
public final class StudentAcademicAdministrationHandlers {
    private final StudentAdmissionService admissions;
    private final StudentOrganizationQuery organizations;
    private final StudentAuthorizationPort authorization;
    private final StudentWriteExecutor writes;
    private final StudentCollegeScopeAuthorizationService collegeScope;

    /** Creates academic administration handlers. */
    public StudentAcademicAdministrationHandlers(StudentAdmissionService admissions,
            StudentOrganizationQuery organizations, StudentAuthorizationPort authorization,
            StudentWriteExecutor writes, StudentCollegeScopeAuthorizationService collegeScope) {
        this.admissions = Objects.requireNonNull(admissions);
        this.organizations = Objects.requireNonNull(organizations);
        this.authorization = Objects.requireNonNull(authorization);
        this.writes = Objects.requireNonNull(writes);
        this.collegeScope = collegeScope;
    }

    /** Registers admission and organization routes. */
    public void register(MessageRouter router) {
        router.register("STUDENT_CREATE", typed(CreateStudentAdmissionCommand.class,
                (message, body) -> write(message, () -> scoped(message, departmentId ->
                        admissions.admit(body, context(message), departmentId)))));
        router.register("STUDENT_CREATE_MANUAL", typed(CreateStudentManualCommand.class,
                (message, body) -> write(message, () -> scoped(message, departmentId ->
                        admissions.createManual(body, context(message), departmentId)))));
        router.register("STUDENT_BATCH_IMPORT", typed(BatchImportCommand.class,
                (message, body) -> write(message, () -> scoped(message, departmentId ->
                        admissions.batchImport(body, context(message), departmentId)))));
        router.register("STUDENT_LIST_DEPARTMENTS", typed(ActiveOnlyQuery.class,
                (message, body) -> listDepartments(message, body)));
        router.register("STUDENT_LIST_MAJORS", typed(OrganizationChildrenQuery.class,
                (message, body) -> listMajors(message, body)));
        router.register("STUDENT_LIST_CLASSES", typed(OrganizationChildrenQuery.class,
                (message, body) -> listClasses(message, body)));
        router.register("STUDENT_SAVE_DEPARTMENT", typed(SaveDepartmentCommand.class,
                (message, body) -> write(message, () -> legacy(message,
                        () -> organizations.saveDepartment(body)))));
        router.register("STUDENT_SAVE_MAJOR", typed(SaveMajorCommand.class,
                (message, body) -> write(message, () -> scoped(message, departmentId ->
                        organizations.saveMajor(body, departmentId)))));
        router.register("STUDENT_SAVE_CLASS", typed(SaveClassCommand.class,
                (message, body) -> write(message, () -> scoped(message, departmentId ->
                        organizations.saveClass(body, departmentId)))));
        router.register("STUDENT_DELETE_CLASS", typed(EntityIdRequest.class,
                (message, body) -> write(message, () -> scoped(message, departmentId -> {
                    organizations.deleteClass(body.entityId(), departmentId);
                    return EmptyResponse.INSTANCE;
                }))));
    }

    private ResponseBody<? extends Serializable> listDepartments(Message message,
            ActiveOnlyQuery query) {
        StudentPrincipal actor = principal(message);
        String departmentId = actor.hasRole("COLLEGE_ADMIN") ? department(actor) : null;
        return success(new ArrayList<>(departmentId == null
                ? organizations.listDepartments(query.activeOnly())
                : organizations.listDepartments(query.activeOnly(), departmentId)));
    }

    private ResponseBody<? extends Serializable> listMajors(Message message,
            OrganizationChildrenQuery query) {
        StudentPrincipal actor = principal(message);
        String departmentId = actor.hasRole("COLLEGE_ADMIN") ? department(actor) : null;
        return departmentId == null && actor.hasRole("COLLEGE_ADMIN") ? forbidden()
                : success(new ArrayList<>(departmentId == null
                ? organizations.listMajors(query.parentId(), query.activeOnly())
                : organizations.listMajors(query.parentId(), query.activeOnly(), departmentId)));
    }

    private ResponseBody<? extends Serializable> listClasses(Message message,
            OrganizationChildrenQuery query) {
        StudentPrincipal actor = principal(message);
        String departmentId = actor.hasRole("COLLEGE_ADMIN") ? department(actor) : null;
        return departmentId == null && actor.hasRole("COLLEGE_ADMIN") ? forbidden()
                : success(new ArrayList<>(departmentId == null
                ? organizations.listClasses(query.parentId(), query.activeOnly())
                : organizations.listClasses(query.parentId(), query.activeOnly(), departmentId)));
    }

    private ResponseBody<? extends Serializable> scoped(Message message,
            java.util.function.Function<String, ? extends Serializable> action) {
        StudentPrincipal actor = principal(message);
        if (actor.hasRole("ADMIN") || actor.hasRole("STUDENT_ADMIN") || actor.hasRole("SUPER_ADMIN")) {
            return success(action.apply(null));
        }
        String departmentId = actor.hasRole("COLLEGE_ADMIN") ? department(actor) : null;
        return departmentId == null ? forbidden() : success(action.apply(departmentId));
    }

    private ResponseBody<? extends Serializable> legacy(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        StudentPrincipal actor = principal(message);
        return (actor.hasRole("ADMIN") || actor.hasRole("STUDENT_ADMIN") || actor.hasRole("SUPER_ADMIN"))
                ? success(action.get()) : forbidden();
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

    private RequestContext context(Message message) {
        return new RequestContext(message.requestId(), principal(message).userId(), "socket");
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
            catch (StudentAdmissionException error) { return ResponseBody.failure(
                    error.code(), error.getMessage(), null); }
            catch (StudentNumberingException error) { return ResponseBody.failure(
                    error.code(), error.getMessage(), null); }
            catch (OrganizationHierarchyException error) { return ResponseBody.failure(
                    "STUDENT_ORGANIZATION_HAS_ACTIVE_CHILDREN", error.getMessage(), null); }
            catch (UnsupportedOperationException error) { return ResponseBody.failure(
                    "COMMON_NOT_SUPPORTED", error.getMessage(), null); }
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
