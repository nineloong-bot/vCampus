package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;

import java.util.List;
import java.util.Objects;

/** Read-only organization hierarchy facade for handlers and clients. */
public final class StudentOrganizationService implements StudentOrganizationQuery {
    private final TransactionManager transactions;
    private final OrganizationRepository organizations;

    public StudentOrganizationService(TransactionManager transactions,
            OrganizationRepository organizations) {
        this.transactions = Objects.requireNonNull(transactions);
        this.organizations = Objects.requireNonNull(organizations);
    }

    public List<DepartmentView> listDepartments(boolean activeOnly) {
        return transactions.inTransaction(connection -> organizations.listDepartments(connection, activeOnly)
                .stream().map(value -> new DepartmentView(value.departmentId(), value.departmentCode(),
                        value.departmentName(), value.active(), value.rowVersion())).toList());
    }

    public List<MajorView> listMajors(String departmentId) {
        return transactions.inTransaction(connection -> organizations.listActiveMajors(connection, departmentId)
                .stream().map(value -> new MajorView(value.majorId(), value.departmentId(), value.majorCode(),
                        value.majorName(), value.active(), value.rowVersion())).toList());
    }

    public List<ClassView> listClasses(String majorId) {
        return transactions.inTransaction(connection -> organizations.listActiveClasses(connection, majorId)
                .stream().map(value -> new ClassView(value.classId(), value.majorId(), value.classCode(),
                        value.className(), value.enrollmentYear(), value.classNumber(), value.active(),
                        value.rowVersion())).toList());
    }
}
