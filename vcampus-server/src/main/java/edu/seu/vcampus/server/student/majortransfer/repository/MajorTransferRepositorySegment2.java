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
abstract class MajorTransferRepositorySegment2 extends MajorTransferRepositorySegment1 {

    // ── Option CRUD ──

    public String insertOption(Connection connection, OptionRow row) {
        String sql = "INSERT INTO tblMajorTransferOption (optionId, batchId, targetMajorId, "
                + "targetDepartmentId, targetMajorName, targetDepartmentName, grades, "
                + "receiveQuota, interviewQuota, writtenPassScore, interviewPassScore, "
                + "writtenWeightPct, interviewWeightPct, difficultyQuotaExempt, requirements, "
                + "isActive, rowVersion, createdAt, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, row.optionId());
            ps.setString(2, row.batchId());
            ps.setString(3, row.targetMajorId());
            ps.setString(4, row.targetDepartmentId());
            ps.setString(5, row.targetMajorName());
            ps.setString(6, row.targetDepartmentName());
            ps.setString(7, row.grades());
            ps.setInt(8, row.receiveQuota());
            ps.setInt(9, row.interviewQuota());
            setDouble(ps, 10, row.writtenPassScore());
            setDouble(ps, 11, row.interviewPassScore());
            ps.setInt(12, row.writtenWeightPct());
            ps.setInt(13, row.interviewWeightPct());
            ps.setBoolean(14, row.difficultyQuotaExempt());
            ps.setString(15, row.requirements());
            ps.setBoolean(16, row.active());
            ps.setLong(17, row.rowVersion());
            ps.setTimestamp(18, Timestamp.from(row.createdAt()));
            ps.setTimestamp(19, Timestamp.from(row.updatedAt()));
            ps.executeUpdate();
            return row.optionId();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot insert option", e);
        }
    }

    public int updateOption(Connection connection, OptionRow row) {
        String sql = "UPDATE tblMajorTransferOption SET grades=?, receiveQuota=?, "
                + "interviewQuota=?, writtenPassScore=?, interviewPassScore=?, "
                + "writtenWeightPct=?, interviewWeightPct=?, difficultyQuotaExempt=?, "
                + "requirements=?, isActive=?, rowVersion=rowVersion+1, updatedAt=? "
                + "WHERE optionId=? AND rowVersion=?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, row.grades());
            ps.setInt(2, row.receiveQuota());
            ps.setInt(3, row.interviewQuota());
            setDouble(ps, 4, row.writtenPassScore());
            setDouble(ps, 5, row.interviewPassScore());
            ps.setInt(6, row.writtenWeightPct());
            ps.setInt(7, row.interviewWeightPct());
            ps.setBoolean(8, row.difficultyQuotaExempt());
            ps.setString(9, row.requirements());
            ps.setBoolean(10, row.active());
            ps.setTimestamp(11, Timestamp.from(row.updatedAt()));
            ps.setString(12, row.optionId());
            ps.setLong(13, row.rowVersion());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot update option", e);
        }
    }

    public Optional<OptionRow> findOption(Connection connection, String optionId) {
        String sql = "SELECT * FROM tblMajorTransferOption WHERE optionId = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, optionId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapOption(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot read option", e);
        }
    }

    public List<OptionRow> listOptionsByBatch(Connection connection, String batchId) {
        String sql = "SELECT * FROM tblMajorTransferOption WHERE batchId = ? ORDER BY targetMajorName";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, batchId);
            try (var rs = ps.executeQuery()) {
                List<OptionRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapOption(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list options", e);
        }
    }
}
