package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.Student;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

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

    public Optional<Student> findById(Connection connection, String studentId) {
        return find(connection, "s.studentId = ?", studentId);
    }

    public Optional<Student> findByUserId(Connection connection, String userId) {
        return find(connection, "s.userId = ?", userId);
    }

    public List<Student> findAll(Connection connection) {
        String sql = "SELECT s.*, c.majorId FROM tblStudent s INNER JOIN tblClass c ON s.classId = c.classId ORDER BY s.studentNumber";
        try (var statement = connection.prepareStatement(sql); var result = statement.executeQuery()) {
            List<Student> values = new ArrayList<>();
            while (result.next()) values.add(map(result));
            return List.copyOf(values);
        } catch (SQLException error) { throw new OrganizationPersistenceException("Cannot list students", error); }
    }

    public void updateContact(Connection connection, String studentId, String email,
            String phone, long expectedVersion, Instant updatedAt) {
        update(connection, "email = ?, phone = ?", statement -> {
            statement.setString(1, email);
            statement.setString(2, phone);
        }, 3, studentId, expectedVersion, updatedAt);
    }

    public void updateStatus(Connection connection, String studentId, String status,
            long expectedVersion, Instant updatedAt) {
        update(connection, "studentStatus = ?", statement -> statement.setString(1, status),
                2, studentId, expectedVersion, updatedAt);
    }

    public void updateEnrollment(Connection connection, String studentId, String classId,
            String studentNumber, long expectedVersion, Instant updatedAt) {
        update(connection, "classId = ?, studentNumber = ?", statement -> {
            statement.setString(1, classId); statement.setString(2, studentNumber);
        }, 3, studentId, expectedVersion, updatedAt);
    }

    private Optional<Student> find(Connection connection, String predicate, String value) {
        String sql = "SELECT s.*, c.majorId FROM tblStudent s INNER JOIN tblClass c ON s.classId = c.classId WHERE " + predicate;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot read student", error);
        }
    }

    private void update(Connection connection, String assignments, Binder binder, int next,
            String studentId, long expectedVersion, Instant updatedAt) {
        String sql = "UPDATE tblStudent SET " + assignments + ", rowVersion = rowVersion + 1, updatedAt = ? WHERE studentId = ? AND rowVersion = ?";
        try (var statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.setTimestamp(next, Timestamp.from(updatedAt));
            statement.setString(next + 1, studentId);
            statement.setLong(next + 2, expectedVersion);
            if (statement.executeUpdate() != 1) throw new ConcurrentModificationException("Student version changed");
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot update student", error);
        }
    }

    private static Student map(ResultSet result) throws SQLException {
        return new Student(result.getString("studentId"), result.getString("userId"),
                result.getString("studentNumber"), edu.seu.vcampus.common.student.StudentType.valueOf(result.getString("studentType")),
                result.getString("studentName"), result.getString("gender"), result.getString("email"),
                result.getString("phone"), result.getString("majorId"), result.getString("classId"),
                result.getDate("enrollmentDate").toLocalDate(), edu.seu.vcampus.common.student.StudentStatus.valueOf(result.getString("studentStatus")),
                result.getLong("rowVersion"), result.getTimestamp("createdAt").toInstant(), result.getTimestamp("updatedAt").toInstant());
    }

    @FunctionalInterface private interface Binder { void bind(java.sql.PreparedStatement statement) throws SQLException; }
}
