package edu.seu.vcampus.server.student.repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

/** Writes immutable student change history inside caller-owned transactions. */
public final class StudentChangeRepository {
    public void insertAdmission(Connection connection, String changeId, String studentId,
            String newValue, String operatorUserId, LocalDate effectiveDate, Instant createdAt) {
        String sql = "INSERT INTO tblStudentChange (changeId, studentId, changeType, oldValue, newValue, reason, operatorUserId, effectiveDate, createdAt) VALUES (?, ?, 'ADMISSION', NULL, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, changeId);
            statement.setString(2, studentId);
            statement.setString(3, newValue);
            statement.setString(4, "新生录取");
            statement.setString(5, operatorUserId);
            statement.setDate(6, Date.valueOf(effectiveDate));
            statement.setTimestamp(7, Timestamp.from(createdAt));
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot insert student change", error);
        }
    }
}
