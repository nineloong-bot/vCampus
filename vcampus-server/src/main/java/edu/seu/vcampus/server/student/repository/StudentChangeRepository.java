package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.common.student.StudentChangeView;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Writes immutable student change history inside caller-owned transactions. */
public final class StudentChangeRepository {
    public List<StudentChangeView> listByStudentId(Connection connection, String studentId) {
        String sql = "SELECT changeId, studentId, changeType, oldValue, newValue, reason, operatorUserId, effectiveDate, createdAt FROM tblStudentChange WHERE studentId = ? ORDER BY createdAt DESC";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (var result = statement.executeQuery()) {
                List<StudentChangeView> values = new ArrayList<>();
                while (result.next()) {
                    var effective = result.getTimestamp("effectiveDate");
                    var created = result.getTimestamp("createdAt");
                    values.add(new StudentChangeView(result.getString("changeId"),
                            result.getString("studentId"), result.getString("changeType"),
                            result.getString("oldValue"), result.getString("newValue"),
                            result.getString("reason"), result.getString("operatorUserId"),
                            effective.toLocalDateTime().toLocalDate(), created.toInstant()));
                }
                return List.copyOf(values);
            }
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot read student changes", error);
        }
    }
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

    public void insertChange(Connection connection, String changeId, String studentId,
            String changeType, String oldValue, String newValue, String reason,
            String operatorUserId, LocalDate effectiveDate, Instant createdAt) {
        String sql = "INSERT INTO tblStudentChange (changeId, studentId, changeType, oldValue, newValue, reason, operatorUserId, effectiveDate, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, changeId); statement.setString(2, studentId);
            statement.setString(3, changeType); statement.setString(4, oldValue);
            statement.setString(5, newValue); statement.setString(6, reason);
            statement.setString(7, operatorUserId); statement.setDate(8, Date.valueOf(effectiveDate));
            statement.setTimestamp(9, Timestamp.from(createdAt)); statement.executeUpdate();
        } catch (SQLException error) { throw new OrganizationPersistenceException("Cannot insert student change", error); }
    }
}
