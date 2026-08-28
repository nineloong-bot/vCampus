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

    public StudentOrganizationAdminService(TransactionManager transactions,
            ResourceLockManager locks, OrganizationRepository organizations) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.organizations = Objects.requireNonNull(organizations);
    }

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

    public MajorView saveMajor(SaveMajorCommand command) {
        String id = blank(command.majorId()) ? UUID.randomUUID().toString() : command.majorId();
        return locks.withLocks(List.of(new ResourceKey("MAJOR", id)), () ->
                transactions.inTransaction(connection -> {
                    var department = organizations.findDepartment(connection, command.departmentId())
                            .filter(Department::active).orElseThrow(() -> new IllegalArgumentException("院系不可用"));
                    var value = new Major(id, department.departmentId(), text(command.code()), text(command.name()), command.active(), 0);
                    if (blank(command.majorId())) organizations.insertMajor(connection, value);
                    else organizations.updateMajor(connection, value, command.expectedVersion());
                    return view(organizations.findMajor(connection, id).orElseThrow());
                }));
    }

    public ClassView saveClass(SaveClassCommand command) {
        String id = blank(command.classId()) ? UUID.randomUUID().toString() : command.classId();
        return locks.withLocks(List.of(new ResourceKey("CLASS", id)), () ->
                transactions.inTransaction(connection -> {
                    var major = organizations.findMajor(connection, command.majorId())
                            .filter(Major::active).orElseThrow(() -> new IllegalArgumentException("专业不可用"));
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
    public List<MajorView> listMajors(String departmentId, boolean activeOnly) {
        return transactions.inTransaction(connection -> organizations.listMajors(connection, departmentId, activeOnly)
                .stream().map(StudentOrganizationAdminService::view).toList());
    }
    @Override public List<ClassView> listClasses(String majorId) { return listClasses(majorId, true); }
    public List<ClassView> listClasses(String majorId, boolean activeOnly) {
        return transactions.inTransaction(connection -> organizations.listClasses(connection, majorId, activeOnly)
                .stream().map(StudentOrganizationAdminService::view).toList());
    }

    private static DepartmentView view(Department v) { return new DepartmentView(v.departmentId(), v.departmentCode(), v.departmentName(), v.active(), v.rowVersion()); }
    private static MajorView view(Major v) { return new MajorView(v.majorId(), v.departmentId(), v.majorCode(), v.majorName(), v.active(), v.rowVersion()); }
    private static ClassView view(StudentClass v) { return new ClassView(v.classId(), v.majorId(), v.classCode(), v.className(), v.enrollmentYear(), v.classNumber(), v.active(), v.rowVersion()); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String text(String value) { if (blank(value)) throw new IllegalArgumentException("必填字段不能为空"); return value.trim(); }
}
