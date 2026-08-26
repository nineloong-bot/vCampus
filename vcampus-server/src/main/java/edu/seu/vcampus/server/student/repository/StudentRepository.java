package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.Student;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;

/** Stores student profiles inside caller-owned transactions. */
public final class StudentRepository {
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
}
