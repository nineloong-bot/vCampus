package edu.seu.vcampus.server.student.majortransfer.repository;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferOptionFinalizationStatus;
import edu.seu.vcampus.server.student.repository.OrganizationPersistenceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persists finalization state and prepared transfers for one transfer option. */
public final class MajorTransferOptionFinalizationRepository {
    /** Lifecycle row for one target-major option. */
    public record OptionFinalizationRow(String optionId,
            MajorTransferOptionFinalizationStatus status, long rowVersion,
            String reviewedBy, Instant reviewedAt, String effectiveBy,
            Instant effectiveAt, Instant createdAt, Instant updatedAt) { }

    /** Immutable prepared transfer snapshot belonging to one application. */
    public record PreparedTransferRow(String applicationId, String batchId,
            String targetDepartmentId, String targetMajorId, String targetClassId,
            int targetCohortYear, long studentVersion, long applicationVersion,
            Instant preparedAt) { }

    /** Returns or initializes the lifecycle row for an option. */
    public OptionFinalizationRow findOrCreate(Connection connection,
            MajorTransferRepository.OptionRow option, Instant now) {
        var found = find(connection, option.optionId());
        if (found.isPresent()) return found.get();
        var status = initialStatus(connection, option.optionId());
        String sql = "INSERT INTO tblMajorTransferOptionFinalization "
                + "(optionId,finalizationStatus,rowVersion,createdAt,updatedAt) VALUES (?,?,?,?,?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, option.optionId());
            statement.setString(2, status.name());
            statement.setLong(3, 0);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
            return find(connection, option.optionId()).orElseThrow();
        } catch (SQLException error) {
            throw failure("Cannot initialize option finalization", error);
        }
    }

    /** Loads an option finalization row when present. */
    public Optional<OptionFinalizationRow> find(Connection connection, String optionId) {
        String sql = "SELECT * FROM tblMajorTransferOptionFinalization WHERE optionId=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, optionId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(new OptionFinalizationRow(
                        result.getString("optionId"),
                        MajorTransferOptionFinalizationStatus.valueOf(
                                result.getString("finalizationStatus")),
                        result.getLong("rowVersion"), result.getString("reviewedBy"),
                        instant(result.getTimestamp("reviewedAt")), result.getString("effectiveBy"),
                        instant(result.getTimestamp("effectiveAt")),
                        result.getTimestamp("createdAt").toInstant(),
                        result.getTimestamp("updatedAt").toInstant())) : Optional.empty();
            }
        } catch (SQLException error) {
            throw failure("Cannot load option finalization", error);
        }
    }

    /** Atomically changes one option lifecycle using optimistic locking. */
    public int updateStatus(Connection connection, String optionId,
            MajorTransferOptionFinalizationStatus from,
            MajorTransferOptionFinalizationStatus to, long expectedVersion,
            String operator, Instant now) {
        String audit = switch (to) {
            case REVIEWED -> ",reviewedBy=?,reviewedAt=?";
            case EFFECTIVE -> ",effectiveBy=?,effectiveAt=?";
            case PROCESSING -> ",reviewedBy=NULL,reviewedAt=NULL,effectiveBy=NULL,effectiveAt=NULL";
        };
        String sql = "UPDATE tblMajorTransferOptionFinalization SET finalizationStatus=?,"
                + "rowVersion=rowVersion+1,updatedAt=?" + audit
                + " WHERE optionId=? AND finalizationStatus=? AND rowVersion=?";
        try (var statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, to.name());
            statement.setTimestamp(index++, Timestamp.from(now));
            if (to != MajorTransferOptionFinalizationStatus.PROCESSING) {
                statement.setString(index++, operator);
                statement.setTimestamp(index++, Timestamp.from(now));
            }
            statement.setString(index++, optionId);
            statement.setString(index++, from.name());
            statement.setLong(index, expectedVersion);
            return statement.executeUpdate();
        } catch (SQLException error) {
            throw failure("Cannot update option finalization", error);
        }
    }

    /** Replaces prepared transfers belonging to one option. */
    public void replacePrepared(Connection connection, String optionId,
            List<PreparedTransferRow> rows) {
        deletePrepared(connection, optionId);
        if (rows.isEmpty()) return;
        String sql = "INSERT INTO tblMajorTransferPreparedTransfer VALUES (?,?,?,?,?,?,?,?,?)";
        try (var statement = connection.prepareStatement(sql)) {
            for (var row : rows) {
                statement.setString(1, row.applicationId()); statement.setString(2, row.batchId());
                statement.setString(3, row.targetDepartmentId()); statement.setString(4, row.targetMajorId());
                statement.setString(5, row.targetClassId()); statement.setInt(6, row.targetCohortYear());
                statement.setLong(7, row.studentVersion()); statement.setLong(8, row.applicationVersion());
                statement.setTimestamp(9, Timestamp.from(row.preparedAt())); statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException error) {
            throw failure("Cannot replace option prepared transfers", error);
        }
    }

    /** Lists prepared transfers belonging to one option. */
    public List<PreparedTransferRow> listPrepared(Connection connection, String optionId) {
        String sql = "SELECT p.* FROM tblMajorTransferPreparedTransfer p INNER JOIN "
                + "tblMajorTransferApplication a ON p.applicationId=a.applicationId "
                + "WHERE a.optionId=? ORDER BY p.applicationId";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, optionId);
            try (var result = statement.executeQuery()) {
                List<PreparedTransferRow> rows = new ArrayList<>();
                while (result.next()) rows.add(new PreparedTransferRow(result.getString("applicationId"),
                        result.getString("batchId"), result.getString("targetDepartmentId"),
                        result.getString("targetMajorId"), result.getString("targetClassId"),
                        result.getInt("targetCohortYear"), result.getLong("studentVersion"),
                        result.getLong("applicationVersion"), result.getTimestamp("preparedAt").toInstant()));
                return List.copyOf(rows);
            }
        } catch (SQLException error) {
            throw failure("Cannot list option prepared transfers", error);
        }
    }

    /** Deletes prepared transfers belonging to one option. */
    public int deletePrepared(Connection connection, String optionId) {
        var rows = listPrepared(connection, optionId);
        try (var statement = connection.prepareStatement(
                "DELETE FROM tblMajorTransferPreparedTransfer WHERE applicationId=?")) {
            for (var row : rows) {
                statement.setString(1, row.applicationId());
                statement.addBatch();
            }
            if (!rows.isEmpty()) statement.executeBatch();
            return rows.size();
        } catch (SQLException error) {
            throw failure("Cannot delete option prepared transfers", error);
        }
    }

    private static MajorTransferOptionFinalizationStatus initialStatus(
            Connection connection, String optionId) {
        String sql = "SELECT applicationStatus FROM tblMajorTransferApplication WHERE optionId=?";
        boolean reviewed = false;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, optionId);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    String status = result.getString(1);
                    if ("EFFECTIVE".equals(status)) return MajorTransferOptionFinalizationStatus.EFFECTIVE;
                    if ("PENDING_EFFECTIVE".equals(status)) reviewed = true;
                }
            }
            return reviewed ? MajorTransferOptionFinalizationStatus.REVIEWED
                    : MajorTransferOptionFinalizationStatus.PROCESSING;
        } catch (SQLException error) {
            throw failure("Cannot derive option finalization", error);
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static OrganizationPersistenceException failure(String message, SQLException error) {
        return new OrganizationPersistenceException(message, error);
    }
}
