package edu.seu.vcampus.server.student.governance;

import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministratorView;

import java.sql.Connection;
import java.util.List;

/** Defines the student college administration repository contract. */
interface StudentCollegeAdministrationRepository {
    List<StudentCollegeAdministratorView> listAdministrators(Connection connection);
    List<DepartmentView> listDepartments(Connection connection);
    void requireDepartment(Connection connection, String id, long version);
    void requireActiveDepartment(Connection connection, String id);
    void requireAdministrator(Connection connection, String userId);
    void requireUnassigned(Connection connection, String userId);
    void requireAssignment(Connection connection, String departmentId,
            String userId, long version);
    long countActive(Connection connection, String departmentId);
    void assign(Connection connection, String departmentId, String userId);
    void deactivate(Connection connection, String departmentId,
            String userId, long version);
    void transfer(Connection connection, String sourceDepartmentId,
                  String targetDepartmentId, String userId, long expectedVersion);
}
