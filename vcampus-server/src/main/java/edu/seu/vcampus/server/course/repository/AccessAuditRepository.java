package edu.seu.vcampus.server.course.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JDBC persistence for immutable enrollment-adjustment and outcome-import audit data. */
final class AccessAuditRepository {
    EnrollmentAdjustment insertAdjustment(Connection c, EnrollmentAdjustment value) {
        Instant operatedAt = value.operatedAt() == null ? Instant.now() : value.operatedAt();
        EnrollmentAdjustment saved = new EnrollmentAdjustment(CourseJdbc.id(value.adjustmentId()), value.studentId(), value.adjustmentType(), value.sourceOfferingId(), value.targetOfferingId(), value.operationResult(), value.failureCode(), operatedAt);
        String sql = "INSERT INTO tblEnrollmentAdjustment (adjustmentId, studentId, adjustmentType, sourceOfferingId, targetOfferingId, operationResult, failureCode, operatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, saved.adjustmentId()); s.setString(2, saved.studentId()); s.setString(3, saved.adjustmentType()); s.setString(4, saved.sourceOfferingId()); s.setString(5, saved.targetOfferingId()); s.setString(6, saved.operationResult()); s.setString(7, saved.failureCode()); s.setTimestamp(8, CourseJdbc.timestamp(saved.operatedAt())); s.executeUpdate(); return saved;
        } catch (SQLException error) { throw CourseJdbc.failure("insert adjustment", error); }
    }

    List<EnrollmentAdjustment> findAdjustmentsByStudent(Connection c, String studentId) {
        List<EnrollmentAdjustment> values = new ArrayList<>();
        String sql = "SELECT * FROM tblEnrollmentAdjustment WHERE studentId=? ORDER BY operatedAt DESC";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, studentId); try (ResultSet r = s.executeQuery()) { while (r.next()) values.add(adjustment(r)); }
            return values;
        } catch (SQLException error) { throw CourseJdbc.failure("list adjustments", error); }
    }
    List<EnrollmentAdjustment> findAdjustments(Connection c){List<EnrollmentAdjustment> v=new ArrayList<>();try(PreparedStatement s=c.prepareStatement("SELECT * FROM tblEnrollmentAdjustment ORDER BY operatedAt DESC");ResultSet r=s.executeQuery()){while(r.next())v.add(adjustment(r));return v;}catch(SQLException e){throw CourseJdbc.failure("list adjustments",e);}}

    private static EnrollmentAdjustment adjustment(ResultSet r) throws SQLException {
        return new EnrollmentAdjustment(r.getString("adjustmentId"), r.getString("studentId"), r.getString("adjustmentType"), r.getString("sourceOfferingId"), r.getString("targetOfferingId"), r.getString("operationResult"), r.getString("failureCode"), CourseJdbc.instant(r, "operatedAt"));
    }

}
