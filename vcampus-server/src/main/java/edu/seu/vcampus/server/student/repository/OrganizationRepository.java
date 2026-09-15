package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** Persists the department-major-class hierarchy. */
public interface OrganizationRepository {
    /**
     * Performs the insert department operation.
     * @param connection the connection
     * @param department the department
     */
    void insertDepartment(Connection connection, Department department);
    /**
     * Performs the insert major operation.
     * @param connection the connection
     * @param major the major
     */
    void insertMajor(Connection connection, Major major);
    /**
     * Performs the insert class operation.
     * @param connection the connection
     * @param studentClass the student class
     */
    void insertClass(Connection connection, StudentClass studentClass);
    /**
     * Performs the find department operation.
     * @param connection the connection
     * @param departmentId the department identifier
     * @return the operation result
     */
    Optional<Department> findDepartment(Connection connection, String departmentId);
    /**
     * Performs the find major operation.
     * @param connection the connection
     * @param majorId the major identifier
     * @return the operation result
     */
    Optional<Major> findMajor(Connection connection, String majorId);
    /**
     * Performs the find class operation.
     * @param connection the connection
     * @param classId the class identifier
     * @return the operation result
     */
    Optional<StudentClass> findClass(Connection connection, String classId);
    /**
     * Performs the list departments operation.
     * @param connection the connection
     * @param activeOnly the active only
     * @return the operation result
     */
    List<Department> listDepartments(Connection connection, boolean activeOnly);
    /**
     * Performs the list majors operation.
     * @param connection the connection
     * @param departmentId the department identifier
     * @param activeOnly the active only
     * @return the operation result
     */
    List<Major> listMajors(Connection connection, String departmentId, boolean activeOnly);
    /**
     * Performs the list classes operation.
     * @param connection the connection
     * @param majorId the major identifier
     * @param activeOnly the active only
     * @return the operation result
     */
    List<StudentClass> listClasses(Connection connection, String majorId, boolean activeOnly);
    /**
     * Performs the list active majors operation.
     * @param connection the connection
     * @param departmentId the department identifier
     * @return the operation result
     */
    List<Major> listActiveMajors(Connection connection, String departmentId);
    /**
     * Performs the list active classes operation.
     * @param connection the connection
     * @param majorId the major identifier
     * @return the operation result
     */
    List<StudentClass> listActiveClasses(Connection connection, String majorId);
    /**
     * Performs the class belongs to operation.
     * @param connection the connection
     * @param classId the class identifier
     * @param majorId the major identifier
     * @param departmentId the department identifier
     * @return the operation result
     */
    boolean classBelongsTo(Connection connection, String classId,
                           String majorId, String departmentId);
    /**
     * Performs the deactivate department operation.
     * @param connection the connection
     * @param departmentId the department identifier
     * @param expectedVersion the expected version
     */
    void deactivateDepartment(Connection connection, String departmentId, long expectedVersion);
    /**
     * Performs the deactivate major operation.
     * @param connection the connection
     * @param majorId the major identifier
     * @param expectedVersion the expected version
     */
    void deactivateMajor(Connection connection, String majorId, long expectedVersion);
    /**
     * Performs the update department operation.
     * @param connection the connection
     * @param department the department
     * @param expectedVersion the expected version
     */
    void updateDepartment(Connection connection, Department department, long expectedVersion);
    /**
     * Performs the update major operation.
     * @param connection the connection
     * @param major the major
     * @param expectedVersion the expected version
     */
    void updateMajor(Connection connection, Major major, long expectedVersion);
    /**
     * Performs the update class operation.
     * @param connection the connection
     * @param studentClass the student class
     * @param expectedVersion the expected version
     */
    void updateClass(Connection connection, StudentClass studentClass, long expectedVersion);
}
