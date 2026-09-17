package edu.seu.vcampus.server.student.majortransfer.repository;

import edu.seu.vcampus.server.student.repository.OrganizationPersistenceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores prepared enrollment snapshots without delete-then-reinsert conflicts in Access. */
final class MajorTransferPreparedTransferRepository {
    void replace(Connection connection, String optionId,
            List<MajorTransferOptionFinalizationRepository.PreparedTransferRow> rows) {
        var existing = list(connection, optionId);
        Set<String> desired = rows.stream().map(
                MajorTransferOptionFinalizationRepository.PreparedTransferRow::applicationId)
                .collect(Collectors.toSet());
        existing.stream().filter(row -> !desired.contains(row.applicationId()))
                .forEach(row -> invalidate(connection, row.applicationId()));
        for (var row : rows) upsert(connection, row);
    }

    List<MajorTransferOptionFinalizationRepository.PreparedTransferRow> list(
            Connection connection, String optionId) {
        String sql = "SELECT p.* FROM tblMajorTransferPreparedTransfer p INNER JOIN "
                + "tblMajorTransferApplication a ON p.applicationId=a.applicationId "
                + "WHERE a.optionId=? AND p.applicationVersion>=0 ORDER BY p.applicationId";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, optionId);
            try (var result = statement.executeQuery()) {
                List<MajorTransferOptionFinalizationRepository.PreparedTransferRow> rows =
                        new ArrayList<>();
                while (result.next()) rows.add(
                        new MajorTransferOptionFinalizationRepository.PreparedTransferRow(
                                result.getString("applicationId"), result.getString("batchId"),
                                result.getString("targetDepartmentId"), result.getString("targetMajorId"),
                                result.getString("targetClassId"), result.getInt("targetCohortYear"),
                                result.getLong("studentVersion"), result.getLong("applicationVersion"),
                                result.getTimestamp("preparedAt").toInstant()));
                return List.copyOf(rows);
            }
        } catch (SQLException error) {
            throw failure("Cannot list option prepared transfers", error);
        }
    }

    int delete(Connection connection, String optionId) {
        var rows = list(connection, optionId);
        rows.forEach(row -> invalidate(connection, row.applicationId()));
        return rows.size();
    }

    private void upsert(Connection connection,
            MajorTransferOptionFinalizationRepository.PreparedTransferRow row) {
        String update = "UPDATE tblMajorTransferPreparedTransfer SET batchId=?,"
                + "targetDepartmentId=?,targetMajorId=?,targetClassId=?,targetCohortYear=?,"
                + "studentVersion=?,applicationVersion=?,preparedAt=? WHERE applicationId=?";
        try (var statement = connection.prepareStatement(update)) {
            bind(statement, row, false);
            statement.setString(9, row.applicationId());
            if (statement.executeUpdate() == 0) insert(connection, row);
        } catch (SQLException error) {
            throw failure("Cannot update option prepared transfer", error);
        }
    }

    private void insert(Connection connection,
            MajorTransferOptionFinalizationRepository.PreparedTransferRow row) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO tblMajorTransferPreparedTransfer VALUES (?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, row.applicationId());
            bind(statement, row, true);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw failure("Cannot insert option prepared transfer", error);
        }
    }

    private void bind(java.sql.PreparedStatement statement,
            MajorTransferOptionFinalizationRepository.PreparedTransferRow row,
            boolean insert) throws SQLException {
        int offset = insert ? 1 : 0;
        statement.setString(1 + offset, row.batchId());
        statement.setString(2 + offset, row.targetDepartmentId());
        statement.setString(3 + offset, row.targetMajorId());
        statement.setString(4 + offset, row.targetClassId());
        statement.setInt(5 + offset, row.targetCohortYear());
        statement.setLong(6 + offset, row.studentVersion());
        statement.setLong(7 + offset, row.applicationVersion());
        statement.setTimestamp(8 + offset, Timestamp.from(row.preparedAt()));
    }

    private void invalidate(Connection connection, String applicationId) {
        try (var statement = connection.prepareStatement(
                "UPDATE tblMajorTransferPreparedTransfer SET applicationVersion=-1 "
                        + "WHERE applicationId=?")) {
            statement.setString(1, applicationId);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw failure("Cannot invalidate option prepared transfer", error);
        }
    }

    private static OrganizationPersistenceException failure(String message, SQLException error) {
        return new OrganizationPersistenceException(message, error);
    }
}
