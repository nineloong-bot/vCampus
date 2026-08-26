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

    boolean insertAttemptIfAbsent(Connection c, CourseAttempt value) {
        if (attemptExists(c, value.sourceReference())) return false;
        CourseAttempt saved = new CourseAttempt(CourseJdbc.id(value.attemptId()), value.studentId(), value.courseId(), value.termId(), value.outcome(), value.sourceReference(), value.importedAt() == null ? Instant.now() : value.importedAt());
        String sql = "INSERT INTO tblCourseAttempt (attemptId, studentId, courseId, termId, outcome, sourceReference, importedAt) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, saved.attemptId()); s.setString(2, saved.studentId()); s.setString(3, saved.courseId()); s.setString(4, saved.termId()); s.setString(5, saved.outcome()); s.setString(6, saved.sourceReference()); s.setTimestamp(7, CourseJdbc.timestamp(saved.importedAt())); s.executeUpdate(); return true;
        } catch (SQLException error) {
            if (attemptExists(c, value.sourceReference())) return false;
            throw CourseJdbc.failure("insert course attempt", error);
        }
    }

    List<CourseAttempt> findAttempts(Connection c, String studentId, String courseId) {
        List<CourseAttempt> values = new ArrayList<>();
        String sql = "SELECT * FROM tblCourseAttempt WHERE studentId=? AND courseId=? ORDER BY importedAt";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, studentId); s.setString(2, courseId); try (ResultSet r = s.executeQuery()) { while (r.next()) values.add(attempt(r)); }
            return values;
        } catch (SQLException error) { throw CourseJdbc.failure("list course attempts", error); }
    }

    boolean existsFailedAttempt(Connection c, String studentId, String courseId) {
        String sql = "SELECT COUNT(*) FROM tblCourseAttempt WHERE studentId=? AND courseId=? AND outcome='FAILED'";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, studentId); s.setString(2, courseId); try (ResultSet r = s.executeQuery()) { return r.next() && r.getInt(1) > 0; }
        } catch (SQLException error) { throw CourseJdbc.failure("check failed course attempt", error); }
    }

    private static boolean attemptExists(Connection c, String sourceReference) {
        try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM tblCourseAttempt WHERE sourceReference=?")) {
            s.setString(1, sourceReference); try (ResultSet r = s.executeQuery()) { return r.next() && r.getInt(1) > 0; }
        } catch (SQLException error) { throw CourseJdbc.failure("check course attempt", error); }
    }

    private static EnrollmentAdjustment adjustment(ResultSet r) throws SQLException {
        return new EnrollmentAdjustment(r.getString("adjustmentId"), r.getString("studentId"), r.getString("adjustmentType"), r.getString("sourceOfferingId"), r.getString("targetOfferingId"), r.getString("operationResult"), r.getString("failureCode"), CourseJdbc.instant(r, "operatedAt"));
    }

    private static CourseAttempt attempt(ResultSet r) throws SQLException {
        return new CourseAttempt(r.getString("attemptId"), r.getString("studentId"), r.getString("courseId"), r.getString("termId"), r.getString("outcome"), r.getString("sourceReference"), CourseJdbc.instant(r, "importedAt"));
    }
}
