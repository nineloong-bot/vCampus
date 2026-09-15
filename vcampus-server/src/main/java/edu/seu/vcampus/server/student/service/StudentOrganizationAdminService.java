package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Transactional administrator facade for the department-major-class hierarchy. */
public final class StudentOrganizationAdminService implements StudentOrganizationQuery {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final OrganizationRepository organizations;

    /**
     * Creates a student organization admin service with its required collaborators.
     * @param transactions the transactions
     * @param locks the locks
     * @param organizations the organizations
     */
    public StudentOrganizationAdminService(TransactionManager transactions,
            ResourceLockManager locks, OrganizationRepository organizations) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.organizations = Objects.requireNonNull(organizations);
    }

    /**
     * Performs the save department operation.
     * @param command the command
     * @return the operation result
     */
    public DepartmentView saveDepartment(SaveDepartmentCommand command) {
        String id = blank(command.departmentId()) ? UUID.randomUUID().toString() : command.departmentId();
        return locks.withLocks(List.of(new ResourceKey("DEPARTMENT", id)), () ->
                transactions.inTransaction(connection -> {
                    var value = new Department(id, text(command.code()), text(command.name()), command.active(), 0);
                    if (blank(command.departmentId())) organizations.insertDepartment(connection, value);
                    else organizations.updateDepartment(connection, value, command.expectedVersion());
                    var saved = organizations.findDepartment(connection, id).orElseThrow();
                    return view(saved);
                }));
    }

    /**
     * Performs the save major operation.
     * @param command the command
     * @return the operation result
     */
    public MajorView saveMajor(SaveMajorCommand command) {
        return saveMajor(command, null);
    }

    @Override public MajorView saveMajor(SaveMajorCommand command, String trustedDepartmentId) {
        String id = blank(command.majorId()) ? UUID.randomUUID().toString() : command.majorId();
        return locks.withLocks(List.of(new ResourceKey("MAJOR", id)), () ->
                transactions.inTransaction(connection -> {
                    if (trustedDepartmentId != null
                            && !trustedDepartmentId.equals(command.departmentId())) forbidden();
                    if (trustedDepartmentId != null && !blank(command.majorId())) {
                        var existing = organizations.findMajor(connection, command.majorId())
                                .orElseThrow(() -> new IllegalArgumentException("专业不存在"));
                        if (!trustedDepartmentId.equals(existing.departmentId())) forbidden();
                    }
                    var department = organizations.findDepartment(connection, command.departmentId())
                            .filter(Department::active).orElseThrow(() -> new IllegalArgumentException("院系不可用"));
                    var value = new Major(id, department.departmentId(), text(command.code()), text(command.name()), command.grades(), command.active(), 0);
                    if (blank(command.majorId())) organizations.insertMajor(connection, value);
                    else organizations.updateMajor(connection, value, command.expectedVersion());
                    return view(organizations.findMajor(connection, id).orElseThrow());
                }));
    }

    /**
     * Performs the save class operation.
     * @param command the command
     * @return the operation result
     */
    public ClassView saveClass(SaveClassCommand command) {
        return saveClass(command, null);
    }

    @Override public ClassView saveClass(SaveClassCommand command, String trustedDepartmentId) {
        String id = blank(command.classId()) ? UUID.randomUUID().toString() : command.classId();
        return locks.withLocks(List.of(new ResourceKey("CLASS", id)), () ->
                transactions.inTransaction(connection -> {
                    var major = organizations.findMajor(connection, command.majorId())
                            .filter(Major::active).orElseThrow(() -> new IllegalArgumentException("专业不可用"));
                    if (trustedDepartmentId != null
                            && !trustedDepartmentId.equals(major.departmentId())) forbidden();
                    if (trustedDepartmentId != null && !blank(command.classId())) {
                        var existing = organizations.findClass(connection, command.classId())
                                .orElseThrow(() -> new IllegalArgumentException("班级不存在"));
                        var existingMajor = organizations.findMajor(connection, existing.majorId())
                                .orElseThrow(() -> new IllegalArgumentException("专业不存在"));
                        if (!trustedDepartmentId.equals(existingMajor.departmentId())) forbidden();
                    }
                    var value = new StudentClass(id, major.majorId(), text(command.code()), text(command.name()),
                            command.enrollmentYear(), command.classNumber(), command.active(), 0);
                    if (blank(command.classId())) organizations.insertClass(connection, value);
                    else organizations.updateClass(connection, value, command.expectedVersion());
                    return view(organizations.findClass(connection, id).orElseThrow());
                }));
    }

    @Override public List<DepartmentView> listDepartments(boolean activeOnly) {
        return transactions.inTransaction(connection -> organizations.listDepartments(connection, activeOnly)
                .stream().map(StudentOrganizationAdminService::view).toList());
    }
    @Override public List<MajorView> listMajors(String departmentId) { return listMajors(departmentId, true); }
    /**
     * Performs the list majors operation.
     * @param departmentId the department identifier
     * @param activeOnly the active only
     * @return the operation result
     */
    public List<MajorView> listMajors(String departmentId, boolean activeOnly) {
        return transactions.inTransaction(connection -> organizations.listMajors(connection, departmentId, activeOnly)
                .stream().map(StudentOrganizationAdminService::view).toList());
    }
    @Override public List<MajorView> listMajors(String departmentId, boolean activeOnly,
            String trustedDepartmentId) {
        return listMajors(trustedDepartmentId, activeOnly);
    }
    @Override public List<ClassView> listClasses(String majorId) { return listClasses(majorId, true); }
    /**
     * Performs the list classes operation.
     * @param majorId the major identifier
     * @param activeOnly the active only
     * @return the operation result
     */
    public List<ClassView> listClasses(String majorId, boolean activeOnly) {
        return transactions.inTransaction(connection -> organizations.listClasses(connection, majorId, activeOnly)
                .stream().map(StudentOrganizationAdminService::view).toList());
    }
    @Override public List<ClassView> listClasses(String majorId, boolean activeOnly,
            String trustedDepartmentId) {
        return transactions.inTransaction(connection -> {
            Major major = organizations.findMajor(connection, majorId).orElseThrow();
            if (!trustedDepartmentId.equals(major.departmentId())) forbidden();
            return organizations.listClasses(connection, majorId, activeOnly).stream()
                    .map(StudentOrganizationAdminService::view).toList();
        });
    }

    private static DepartmentView view(Department v) { return new DepartmentView(v.departmentId(), v.departmentCode(), v.departmentName(), v.active(), v.rowVersion()); }
    private static MajorView view(Major v) { return new MajorView(v.majorId(), v.departmentId(), v.majorCode(), v.majorName(), v.grades(), v.active(), v.rowVersion()); }
    private static ClassView view(StudentClass v) { return new ClassView(v.classId(), v.majorId(), v.classCode(), v.className(), v.enrollmentYear(), v.classNumber(), v.active(), v.rowVersion()); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String text(String value) { if (blank(value)) throw new IllegalArgumentException("必填字段不能为空"); return value.trim(); }
    private static void forbidden() { throw new IllegalArgumentException("COMMON_FORBIDDEN"); }
}
