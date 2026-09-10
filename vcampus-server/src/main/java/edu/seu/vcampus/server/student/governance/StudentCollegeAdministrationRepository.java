package edu.seu.vcampus.server.student.governance;
import java.sql.Connection;
interface StudentCollegeAdministrationRepository {
    void requireDepartment(Connection connection,String id,long version);
    void requireAdministrator(Connection connection,String userId);
    void requireUnassigned(Connection connection,String userId);
    void requireAssignment(Connection connection, String departmentId, String userId, long version);
    long countActive(Connection connection,String departmentId);
    void assign(Connection connection,String departmentId,String userId);
    void deactivate(Connection connection,String departmentId,String userId,long version);
    void transfer(Connection connection, String sourceDepartmentId,
                  String targetDepartmentId, String userId, long expectedVersion);
}
