package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

/** Implements a focused group of AccessOrganizationRepository persistence operations. */
abstract class AccessOrganizationRepositoryOperations1 extends AccessOrganizationRepositoryOperations2 {

    @Override
    public void insertDepartment(Connection connection, Department department) {
        executeInsert(connection,
                "INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES (?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, department.departmentId());
                    statement.setString(2, department.departmentCode());
                    statement.setString(3, department.departmentName());
                    statement.setBoolean(4, department.active());
                    statement.setLong(5, department.rowVersion());
                });
    }

    @Override
    public void insertMajor(Connection connection, Major major) {
        executeInsert(connection,
                "INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, major.majorId());
                    statement.setString(2, major.departmentId());
                    statement.setString(3, major.majorCode());
                    statement.setString(4, major.majorName());
                    statement.setString(5, major.grades());
                    statement.setBoolean(6, major.active());
                    statement.setLong(7, major.rowVersion());
                });
    }

    @Override
    public void insertClass(Connection connection, StudentClass studentClass) {
        executeInsert(connection,
                "INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, studentClass.classId());
                    statement.setString(2, studentClass.majorId());
                    statement.setString(3, studentClass.classCode());
                    statement.setString(4, studentClass.className());
                    statement.setInt(5, studentClass.enrollmentYear());
                    statement.setInt(6, studentClass.classNumber());
                    statement.setBoolean(7, studentClass.active());
                    statement.setLong(8, studentClass.rowVersion());
                });
        Major major = findMajor(connection, studentClass.majorId()).orElseThrow(() ->
                new OrganizationHierarchyException("Class major does not exist"));
        String year = String.format("%02d", studentClass.enrollmentYear() % 100);
        String sequenceKey = "STUDENT_NUMBER:" + major.majorCode() + ":" + year
                + ":" + studentClass.classNumber();
        sequences.getOrCreate(connection, sequenceKey, 99);
    }

    @Override
    public Optional<Department> findDepartment(Connection connection, String departmentId) {
        String sql = "SELECT departmentId, departmentCode, departmentName, isActive, rowVersion FROM tblDepartment WHERE departmentId = ?";
        return queryOne(connection, sql, departmentId, this::mapDepartment);
    }

    @Override
    public Optional<Major> findMajor(Connection connection, String majorId) {
        String sql = "SELECT majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion FROM tblMajor WHERE majorId = ?";
        return queryOne(connection, sql, majorId, this::mapMajor);
    }

    @Override
    public Optional<StudentClass> findClass(Connection connection, String classId) {
        String sql = "SELECT classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion FROM tblClass WHERE classId = ?";
        return queryOne(connection, sql, classId, this::mapClass);
    }

    @Override
    public List<Department> listDepartments(Connection connection, boolean activeOnly) {
        String sql = "SELECT departmentId, departmentCode, departmentName, isActive, rowVersion FROM tblDepartment"
                + (activeOnly ? " WHERE isActive = TRUE" : "") + " ORDER BY departmentCode";
        try (var statement = connection.prepareStatement(sql); var result = statement.executeQuery()) {
            List<Department> values = new ArrayList<>();
            while (result.next()) values.add(mapDepartment(result));
            return List.copyOf(values);
        } catch (SQLException error) { throw failure("Cannot list departments", error); }
    }

    @Override
    public List<Major> listActiveMajors(Connection connection, String departmentId) {
        return listMajors(connection, departmentId, true);
    }

    @Override
    public List<Major> listMajors(Connection connection, String departmentId, boolean activeOnly) {
        String sql = "SELECT majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion FROM tblMajor WHERE departmentId = ? AND isActive = TRUE ORDER BY majorCode";
        if (!activeOnly) sql = "SELECT majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion FROM tblMajor WHERE departmentId = ? ORDER BY majorCode";
        return queryMany(connection, sql, departmentId, this::mapMajor);
    }

    @Override
    public List<StudentClass> listActiveClasses(Connection connection, String majorId) {
        return listClasses(connection, majorId, true);
    }

    @Override
    public List<StudentClass> listClasses(Connection connection, String majorId, boolean activeOnly) {
        String sql = "SELECT classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion FROM tblClass WHERE majorId = ? AND isActive = TRUE ORDER BY enrollmentYear, classNumber";
        if (!activeOnly) sql = "SELECT classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion FROM tblClass WHERE majorId = ? ORDER BY enrollmentYear, classNumber";
        return queryMany(connection, sql, majorId, this::mapClass);
    }

    @Override
    public void updateDepartment(Connection connection, Department value, long expectedVersion) {
        if (!value.active() && count(connection,
                "SELECT COUNT(*) FROM tblMajor WHERE departmentId = ? AND isActive = TRUE", value.departmentId()) > 0)
            throw new OrganizationHierarchyException("Department has active majors");
        update(connection, "UPDATE tblDepartment SET departmentCode=?, departmentName=?, isActive=?, rowVersion=rowVersion+1 WHERE departmentId=? AND rowVersion=?",
                statement -> { statement.setString(1, value.departmentCode()); statement.setString(2, value.departmentName());
                    statement.setBoolean(3, value.active()); statement.setString(4, value.departmentId()); statement.setLong(5, expectedVersion); });
    }
}
