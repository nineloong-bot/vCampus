package edu.seu.vcampus.server.student.majortransfer.repository;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferCollegeStatus;
import edu.seu.vcampus.server.student.repository.OrganizationPersistenceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persists target-college batch lifecycle and prepared transfer snapshots. */
public final class MajorTransferCollegeBatchRepository {
    /** One target college's lifecycle inside a school-wide batch. */
    public record CollegeBatchRow(String batchId, String targetDepartmentId,
            MajorTransferCollegeStatus status, long rowVersion, String reviewedBy,
            Instant reviewedAt, String effectiveBy, Instant effectiveAt,
            Instant createdAt, Instant updatedAt) { }

    /** Immutable input snapshot for one prepared transfer. */
    public record PreparedTransferRow(String applicationId, String batchId,
            String targetDepartmentId, String targetMajorId, String targetClassId,
            int targetCohortYear, long studentVersion, long applicationVersion,
            Instant preparedAt) { }

    /** Returns or initializes one target college lifecycle row. */
    public CollegeBatchRow findOrCreate(Connection connection, String batchId,
            String departmentId, Instant now) {
        var found = find(connection, batchId, departmentId);
        if (found.isPresent()) return found.get();
        String sql = "INSERT INTO tblMajorTransferBatchCollege "
                + "(batchId,targetDepartmentId,collegeStatus,rowVersion,createdAt,updatedAt) "
                + "VALUES (?,?,?,?,?,?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, batchId); statement.setString(2, departmentId);
            statement.setString(3, MajorTransferCollegeStatus.PROCESSING.name());
            statement.setLong(4, 0); statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now)); statement.executeUpdate();
            return find(connection, batchId, departmentId).orElseThrow();
        } catch (SQLException error) {
            throw failure("Cannot initialize college batch", error);
        }
    }

    /** Loads one target college lifecycle row. */
    public Optional<CollegeBatchRow> find(Connection connection, String batchId,
            String departmentId) {
        String sql = "SELECT * FROM tblMajorTransferBatchCollege WHERE batchId=? AND targetDepartmentId=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, batchId); statement.setString(2, departmentId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(new CollegeBatchRow(result.getString("batchId"),
                        result.getString("targetDepartmentId"), MajorTransferCollegeStatus.valueOf(
                                result.getString("collegeStatus")), result.getLong("rowVersion"),
                        result.getString("reviewedBy"), instant(result.getTimestamp("reviewedAt")),
                        result.getString("effectiveBy"), instant(result.getTimestamp("effectiveAt")),
                        result.getTimestamp("createdAt").toInstant(),
                        result.getTimestamp("updatedAt").toInstant())) : Optional.empty();
            }
        } catch (SQLException error) {
            throw failure("Cannot load college batch", error);
        }
    }

    /** Atomically changes one college lifecycle with optimistic locking. */
    public int updateStatus(Connection connection, String batchId, String departmentId,
            MajorTransferCollegeStatus from, MajorTransferCollegeStatus to, long expectedVersion,
            String operator, Instant now) {
        String audit = switch (to) {
            case REVIEWED -> ",reviewedBy=?,reviewedAt=?";
            case EFFECTIVE -> ",effectiveBy=?,effectiveAt=?";
            case PROCESSING -> ",reviewedBy=NULL,reviewedAt=NULL,effectiveBy=NULL,effectiveAt=NULL";
        };
        String sql = "UPDATE tblMajorTransferBatchCollege SET collegeStatus=?,rowVersion=rowVersion+1,updatedAt=?"
                + audit + " WHERE batchId=? AND targetDepartmentId=? AND collegeStatus=? AND rowVersion=?";
        try (var statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, to.name()); statement.setTimestamp(index++, Timestamp.from(now));
            if (to != MajorTransferCollegeStatus.PROCESSING) {
                statement.setString(index++, operator); statement.setTimestamp(index++, Timestamp.from(now));
            }
            statement.setString(index++, batchId); statement.setString(index++, departmentId);
            statement.setString(index++, from.name()); statement.setLong(index, expectedVersion);
            return statement.executeUpdate();
        } catch (SQLException error) {
            throw failure("Cannot update college batch", error);
        }
    }

    /** Replaces all prepared transfers for one college. */
    public void replacePrepared(Connection connection, String batchId, String departmentId,
            List<PreparedTransferRow> rows) {
        deletePrepared(connection, batchId, departmentId);
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
            throw failure("Cannot replace prepared transfers", error);
        }
    }

    /** Lists prepared transfers for one target college. */
    public List<PreparedTransferRow> listPrepared(Connection connection, String batchId,
            String departmentId) {
        String sql = "SELECT * FROM tblMajorTransferPreparedTransfer WHERE batchId=? AND targetDepartmentId=? ORDER BY applicationId";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, batchId); statement.setString(2, departmentId);
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
            throw failure("Cannot list prepared transfers", error);
        }
    }

    /** Deletes prepared transfers for one target college. */
    public int deletePrepared(Connection connection, String batchId, String departmentId) {
        try (var statement = connection.prepareStatement(
                "DELETE FROM tblMajorTransferPreparedTransfer WHERE batchId=? AND targetDepartmentId=?")) {
            statement.setString(1, batchId); statement.setString(2, departmentId);
            return statement.executeUpdate();
        } catch (SQLException error) {
            throw failure("Cannot delete prepared transfers", error);
        }
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    private static OrganizationPersistenceException failure(String message, SQLException error) {
        return new OrganizationPersistenceException(message, error);
    }
}
