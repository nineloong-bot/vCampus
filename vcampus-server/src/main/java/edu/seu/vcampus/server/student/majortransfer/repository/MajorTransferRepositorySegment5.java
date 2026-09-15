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
abstract class MajorTransferRepositorySegment5 extends MajorTransferRepositorySegment4 {

    public List<ApplicationRow> listApplicationsByBatch(Connection connection, String batchId) {
        String sql = "SELECT * FROM tblMajorTransferApplication "
                + "WHERE batchId = ? AND applicationStatus <> 'DRAFT' ORDER BY createdAt";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, batchId);
            try (var rs = ps.executeQuery()) {
                List<ApplicationRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapApplication(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list applications by batch", e);
        }
    }

    public List<ApplicationRow> listApplicationsByBatchAndCollege(Connection connection,
            String batchId, String departmentId) {
        String sql = """
                SELECT a.*
                FROM tblMajorTransferApplication a
                INNER JOIN tblMajorTransferOption o ON a.optionId=o.optionId
                WHERE a.batchId=?
                  AND a.applicationStatus <> 'DRAFT'
                  AND (a.fromDepartmentId=? OR o.targetDepartmentId=?)
                ORDER BY a.createdAt
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, batchId);
            statement.setString(2, departmentId);
            statement.setString(3, departmentId);
            try (var result = statement.executeQuery()) {
                List<ApplicationRow> rows = new ArrayList<>();
                while (result.next()) rows.add(mapApplication(result));
                return List.copyOf(rows);
            }
        } catch (SQLException error) {
            throw new OrganizationPersistenceException(
                    "Cannot list applications by batch and college", error);
        }
    }

    public boolean hasSuccessfulTransfer(Connection connection, String studentId) {
        String sql = "SELECT COUNT(*) FROM tblMajorTransferApplication "
                + "WHERE studentId = ? AND applicationStatus IN ('PENDING_EFFECTIVE', 'EFFECTIVE')";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot check successful transfer", e);
        }
    }

    // ── Attachment CRUD ──

    public edu.seu.vcampus.common.student.majortransfer.MajorTransferAttachmentDocument readAttachment(Connection connection, String id) {
        try (var statement = connection.prepareStatement("SELECT fileName, contentType, content FROM tblMajorTransferAttachment WHERE attachmentId=?")) {
            statement.setString(1, id);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("附件不存在");
                return new edu.seu.vcampus.common.student.majortransfer.MajorTransferAttachmentDocument(
                        result.getString(1), result.getString(2), result.getBytes(3));
            }
        } catch (SQLException error) { throw new OrganizationPersistenceException("Cannot read attachment", error); }
    }

    public String insertAttachment(Connection connection, String attachmentId,
                                   String applicationId, String fileName, String contentType,
                                   long fileSize, byte[] content, Instant createdAt) {
        String sql = "INSERT INTO tblMajorTransferAttachment "
                + "(attachmentId, applicationId, fileName, contentType, fileSize, content, createdAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, attachmentId);
            ps.setString(2, applicationId);
            ps.setString(3, fileName);
            ps.setString(4, contentType);
            ps.setLong(5, fileSize);
            ps.setBytes(6, content);
            ps.setTimestamp(7, Timestamp.from(createdAt));
            ps.executeUpdate();
            return attachmentId;
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot insert attachment", e);
        }
    }

    public List<AttachmentRow> listAttachments(Connection connection, String applicationId) {
        String sql = "SELECT attachmentId, applicationId, fileName, contentType, fileSize, createdAt "
                + "FROM tblMajorTransferAttachment WHERE applicationId = ? ORDER BY createdAt";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, applicationId);
            try (var rs = ps.executeQuery()) {
                List<AttachmentRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapAttachment(rs));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list attachments", e);
        }
    }

    public int countAttachments(Connection connection, String applicationId) {
        String sql = "SELECT COUNT(*) FROM tblMajorTransferAttachment WHERE applicationId = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, applicationId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot count attachments", e);
        }
    }
}
