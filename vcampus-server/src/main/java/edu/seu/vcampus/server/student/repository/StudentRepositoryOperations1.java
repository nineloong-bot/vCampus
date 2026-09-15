package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.common.student.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/** Implements a focused group of StudentRepository persistence operations. */
abstract class StudentRepositoryOperations1 extends StudentRepositoryOperations2 {
    public boolean existsByStudentNumber(Connection connection, String studentNumber) {
        return exists(connection, "studentNumber", studentNumber);
    }

    public boolean existsByIdDocumentNumber(Connection connection, String idDocumentNumber) {
        return exists(connection, "idDocumentNumber", idDocumentNumber);
    }

    public void insert(Connection connection, Student student) {
        String sql = "INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, student.studentId());
            statement.setString(2, student.userId());
            statement.setString(3, student.studentNumber());
            statement.setString(4, student.studentType().name());
            statement.setString(5, student.studentName());
            statement.setString(6, student.gender());
            statement.setString(7, student.email());
            statement.setString(8, student.phone());
            statement.setString(9, student.classId());
            statement.setDate(10, Date.valueOf(student.enrollmentDate()));
            statement.setString(11, student.status().name());
            statement.setLong(12, student.rowVersion());
            statement.setTimestamp(13, Timestamp.from(student.createdAt()));
            statement.setTimestamp(14, Timestamp.from(student.updatedAt()));
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot insert student", error);
        }
    }

    public void insertManual(Connection connection, Student student, String idDocumentType,
            String idDocumentNumber, LocalDate birthDate) {
        String sql = "INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, "
                + "studentName, gender, email, phone, idDocumentType, idDocumentNumber, birthDate, "
                + "classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, student.studentId());
            statement.setString(2, student.userId());
            statement.setString(3, student.studentNumber());
            statement.setString(4, student.studentType().name());
            statement.setString(5, student.studentName());
            statement.setString(6, student.gender());
            statement.setString(7, idDocumentType);
            statement.setString(8, idDocumentNumber);
            setDate(statement, 9, birthDate);
            statement.setString(10, student.classId());
            setDate(statement, 11, student.enrollmentDate());
            statement.setString(12, student.status().name());
            statement.setLong(13, student.rowVersion());
            statement.setTimestamp(14, Timestamp.from(student.createdAt()));
            statement.setTimestamp(15, Timestamp.from(student.updatedAt()));
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot insert manual student", error);
        }
    }

    public Optional<Student> findById(Connection connection, String studentId) {
        return find(connection, "s.studentId = ?", studentId);
    }

    public Optional<Student> findByUserId(Connection connection, String userId) {
        return find(connection, "s.userId = ?", userId);
    }

    public Optional<Student> findByStudentNumber(Connection connection, String studentNumber) {
        return find(connection, "s.studentNumber = ?", studentNumber);
    }

    public List<Student> findAll(Connection connection) {
        String sql = "SELECT s.*, c.majorId FROM tblStudent s INNER JOIN tblClass c ON s.classId = c.classId ORDER BY s.studentNumber";
        return list(connection, sql, null);
    }

    /** Lists students whose current class and major belong to the trusted department. */
    public List<Student> findAll(Connection connection, String departmentId) {
        String sql = """
                SELECT s.*, c.majorId
                FROM (tblStudent s INNER JOIN tblClass c ON s.classId=c.classId)
                INNER JOIN tblMajor m ON c.majorId=m.majorId
                WHERE m.departmentId=?
                ORDER BY s.studentNumber
                """;
        return list(connection, sql, departmentId);
    }

    /** Returns whether the student's current class belongs to the trusted department. */
    public boolean belongsToDepartment(Connection connection, String studentId,
            String departmentId) {
        String sql = """
                SELECT COUNT(*)
                FROM (tblStudent s INNER JOIN tblClass c ON s.classId=c.classId)
                INNER JOIN tblMajor m ON c.majorId=m.majorId
                WHERE s.studentId=? AND m.departmentId=?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            statement.setString(2, departmentId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1) == 1;
            }
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot check student department", error);
        }
    }

    public StudentProfileData findProfileByUserId(Connection connection, String userId,
            String campusCardNumber) {
        return findProfile(connection, "s.userId = ?", userId, campusCardNumber);
    }
}
