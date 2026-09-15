package edu.seu.vcampus.server.student.majortransfer.repository;

import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.student.repository.OrganizationPersistenceException;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository.*;

/** Implements a focused group of major-transfer persistence operations. */
abstract class MajorTransferRepositorySegment1 extends MajorTransferRepositoryMappings {

    // ── Batch CRUD ──

    public String insertBatch(Connection connection, BatchRow row) {
        String sql = "INSERT INTO tblMajorTransferBatch (batchId, batchName, batchStatus, "
                + "applicationStart, applicationEnd, publicityStart, publicityEnd, effectiveDate, "
                + "rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, row.batchId());
            ps.setString(2, row.batchName());
            ps.setString(3, row.status().name());
            ps.setTimestamp(4, Timestamp.from(row.applicationStart()));
            ps.setTimestamp(5, Timestamp.from(row.applicationEnd()));
            setTimestamp(ps, 6, row.publicityStart());
            setTimestamp(ps, 7, row.publicityEnd());
            setTimestamp(ps, 8, row.effectiveDate());
            ps.setLong(9, row.rowVersion());
            ps.setTimestamp(10, Timestamp.from(row.createdAt()));
            ps.setTimestamp(11, Timestamp.from(row.updatedAt()));
            ps.executeUpdate();
            return row.batchId();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot insert batch", e);
        }
    }

    public int updateBatch(Connection connection, String batchId, String batchName,
                           MajorTransferBatchStatus status, Instant applicationStart,
                           Instant applicationEnd, Instant publicityStart, Instant publicityEnd,
                           Instant effectiveDate, long expectedVersion, Instant updatedAt) {
        String sql = "UPDATE tblMajorTransferBatch SET batchName=?, batchStatus=?, "
                + "applicationStart=?, applicationEnd=?, publicityStart=?, publicityEnd=?, "
                + "effectiveDate=?, rowVersion=rowVersion+1, updatedAt=? "
                + "WHERE batchId=? AND rowVersion=?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, batchName);
            ps.setString(2, status.name());
            ps.setTimestamp(3, Timestamp.from(applicationStart));
            ps.setTimestamp(4, Timestamp.from(applicationEnd));
            setTimestamp(ps, 5, publicityStart);
            setTimestamp(ps, 6, publicityEnd);
            setTimestamp(ps, 7, effectiveDate);
            ps.setTimestamp(8, Timestamp.from(updatedAt));
            ps.setString(9, batchId);
            ps.setLong(10, expectedVersion);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot update batch", e);
        }
    }

    public Optional<BatchRow> findBatch(Connection connection, String batchId) {
        String sql = "SELECT * FROM tblMajorTransferBatch WHERE batchId = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, batchId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapBatch(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot read batch", e);
        }
    }

    public List<BatchRow> listBatches(Connection connection) {
        String sql = "SELECT * FROM tblMajorTransferBatch ORDER BY createdAt DESC";
        try (var ps = connection.prepareStatement(sql); var rs = ps.executeQuery()) {
            List<BatchRow> rows = new ArrayList<>();
            while (rs.next()) rows.add(mapBatch(rs));
            return List.copyOf(rows);
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list batches", e);
        }
    }

    public Optional<BatchRow> findOpenBatchAt(Connection connection, Instant instant) {
        String sql = "SELECT * FROM tblMajorTransferBatch WHERE batchStatus = 'OPEN' "
                + "AND applicationStart <= ? AND applicationEnd >= ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(instant));
            ps.setTimestamp(2, Timestamp.from(instant));
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapBatch(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot find open batch", e);
        }
    }

    public List<BatchRow> findOverlappingOpenBatches(Connection connection,
            Instant start, Instant end, String excludeBatchId) {
        String sql = "SELECT * FROM tblMajorTransferBatch "
                + "WHERE batchStatus = 'OPEN' AND applicationStart <= ? AND applicationEnd >= ?";
        if (excludeBatchId != null) sql += " AND batchId <> ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(end));
            ps.setTimestamp(2, Timestamp.from(start));
            if (excludeBatchId != null) ps.setString(3, excludeBatchId);
            try (var rs = ps.executeQuery()) {
                List<BatchRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapBatch(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot check overlapping batches", e);
        }
    }
}
