package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** Persists the department-major-class hierarchy. */
public interface OrganizationRepository {
    void insertDepartment(Connection connection, Department department);
    void insertMajor(Connection connection, Major major);
    void insertClass(Connection connection, StudentClass studentClass);
    Optional<Department> findDepartment(Connection connection, String departmentId);
    Optional<Major> findMajor(Connection connection, String majorId);
    Optional<StudentClass> findClass(Connection connection, String classId);
    List<Department> listDepartments(Connection connection, boolean activeOnly);
    List<Major> listActiveMajors(Connection connection, String departmentId);
    List<StudentClass> listActiveClasses(Connection connection, String majorId);
    boolean classBelongsTo(Connection connection, String classId,
                           String majorId, String departmentId);
    void deactivateDepartment(Connection connection, String departmentId, long expectedVersion);
    void deactivateMajor(Connection connection, String majorId, long expectedVersion);
}
