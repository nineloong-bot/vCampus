package edu.seu.vcampus.server.course.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC persistence for retained enrollment records and offering counters. */
final class AccessEnrollmentRepository {
    private final AccessOfferingRepository offerings = new AccessOfferingRepository();

    Optional<Enrollment> findEnrollment(Connection c, String studentId, String offeringId) {
        String sql = "SELECT * FROM tblEnrollment WHERE studentId=? AND offeringId=?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, studentId); s.setString(2, offeringId);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(enrollment(r)) : Optional.empty(); }
        } catch (SQLException error) { throw CourseJdbc.failure("read enrollment", error); }
    }

    boolean existsEnrollmentForOffering(Connection c, String offeringId) {
        try (PreparedStatement statement = c.prepareStatement(
                "SELECT COUNT(*) FROM tblEnrollment WHERE offeringId=?")) {
            statement.setString(1, offeringId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getLong(1) > 0;
            }
        } catch (SQLException error) {
            throw CourseJdbc.failure("check offering enrollment history", error);
        }
    }

    Enrollment requireEnrollment(Connection c, String enrollmentId) {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM tblEnrollment WHERE enrollmentId=?")) {
            s.setString(1, enrollmentId); try (ResultSet r = s.executeQuery()) { if (r.next()) return enrollment(r); }
        } catch (SQLException error) { throw CourseJdbc.failure("read enrollment", error); }
        throw CourseJdbc.missing("Enrollment", enrollmentId);
    }

    List<Enrollment> findActiveByStudentAndTerm(Connection c, String studentId, String termId) {
        List<Enrollment> values = new ArrayList<>();
        String sql = "SELECT e.* FROM tblEnrollment e INNER JOIN tblCourseOffering o ON e.offeringId=o.offeringId WHERE e.studentId=? AND o.termId=? AND e.enrollmentStatus='ACTIVE' ORDER BY e.enrolledAt";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, studentId); s.setString(2, termId);
            try (ResultSet r = s.executeQuery()) { while (r.next()) values.add(enrollment(r)); }
            return values;
        } catch (SQLException error) { throw CourseJdbc.failure("list active enrollments", error); }
    }

    List<Enrollment> findByStudentAndTerm(Connection c, String studentId, String termId) {
        List<Enrollment> values = new ArrayList<>();
        String sql = "SELECT e.* FROM tblEnrollment e INNER JOIN tblCourseOffering o ON "
                + "e.offeringId=o.offeringId WHERE e.studentId=? AND o.termId=? ORDER BY e.enrolledAt";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, studentId);
            s.setString(2, termId);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) values.add(enrollment(r));
            }
            return values;
        } catch (SQLException error) {
            throw CourseJdbc.failure("list enrollment history", error);
        }
    }

    List<Enrollment> findActiveByStudent(Connection c,String studentId){
        List<Enrollment> values=new ArrayList<>();
        try(PreparedStatement s=c.prepareStatement("SELECT * FROM tblEnrollment WHERE studentId=? AND enrollmentStatus='ACTIVE' ORDER BY enrolledAt")){s.setString(1,studentId);try(ResultSet r=s.executeQuery()){while(r.next())values.add(enrollment(r));}return values;}catch(SQLException e){throw CourseJdbc.failure("list active enrollments",e);}
    }

    Enrollment insertEnrollment(Connection c, Enrollment value) {
        Optional<Enrollment> existing = findEnrollment(c, value.studentId(), value.offeringId());
        if (existing.isPresent()) {
            Enrollment retained = existing.get();
            if (!"DROPPED".equals(retained.enrollmentStatus())) {
                throw new IllegalStateException("Enrollment already active: " + retained.enrollmentId());
            }
            return reactivate(c, retained, value);
        }
        Instant now = Instant.now(); Instant enrolledAt = value.enrolledAt() == null ? now : value.enrolledAt();
        Enrollment saved = new Enrollment(CourseJdbc.id(value.enrollmentId()), value.offeringId(), value.studentId(), value.enrollmentType(), value.enrollmentStatus(), enrolledAt, value.droppedAt(), 0, now, now);
        String sql = "INSERT INTO tblEnrollment (enrollmentId, offeringId, studentId, enrollmentType, enrollmentStatus, enrolledAt, droppedAt, rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            setEnrollment(s, saved); s.executeUpdate(); return saved;
        } catch (SQLException error) { throw CourseJdbc.failure("insert enrollment", error); }
    }

    Enrollment updateEnrollment(Connection c, Enrollment value, long expected) {
        Instant now = Instant.now();
        String sql = "UPDATE tblEnrollment SET enrollmentType=?, enrollmentStatus=?, enrolledAt=?, droppedAt=?, rowVersion=?, updatedAt=? WHERE enrollmentId=? AND rowVersion=?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, value.enrollmentType()); s.setString(2, value.enrollmentStatus()); s.setTimestamp(3, CourseJdbc.timestamp(value.enrolledAt()));
            if (value.droppedAt() == null) s.setNull(4, java.sql.Types.TIMESTAMP); else s.setTimestamp(4, CourseJdbc.timestamp(value.droppedAt()));
            s.setLong(5, expected + 1); s.setTimestamp(6, CourseJdbc.timestamp(now)); s.setString(7, value.enrollmentId()); s.setLong(8, expected);
            if (s.executeUpdate() != 1) throw CourseJdbc.stale("enrollment", value.enrollmentId());
            return new Enrollment(value.enrollmentId(), value.offeringId(), value.studentId(), value.enrollmentType(), value.enrollmentStatus(), value.enrolledAt(), value.droppedAt(), expected + 1, value.createdAt(), now);
        } catch (SQLException error) { throw CourseJdbc.failure("update enrollment", error); }
    }

    Offering changeEnrolledCount(Connection c, String offeringId, int delta) {
        Instant now = Instant.now();
        String sql = "UPDATE tblCourseOffering SET enrolledCount=enrolledCount+?, rowVersion=rowVersion+1, updatedAt=? WHERE offeringId=?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setInt(1, delta); s.setTimestamp(2, CourseJdbc.timestamp(now)); s.setString(3, offeringId);
            if (s.executeUpdate() != 1) throw CourseJdbc.missing("Offering", offeringId);
            return offerings.requireOffering(c, offeringId);
        } catch (SQLException error) { throw CourseJdbc.failure("change enrolled count", error); }
    }

    private Enrollment reactivate(Connection c, Enrollment retained, Enrollment requested) {
        Instant now = Instant.now(); Instant enrolledAt = requested.enrolledAt() == null ? now : requested.enrolledAt();
        String sql = "UPDATE tblEnrollment SET enrollmentType=?, enrollmentStatus='ACTIVE', enrolledAt=?, droppedAt=?, rowVersion=?, updatedAt=? WHERE enrollmentId=? AND rowVersion=?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, requested.enrollmentType()); s.setTimestamp(2, CourseJdbc.timestamp(enrolledAt)); s.setNull(3, java.sql.Types.TIMESTAMP); s.setLong(4, retained.rowVersion() + 1); s.setTimestamp(5, CourseJdbc.timestamp(now)); s.setString(6, retained.enrollmentId()); s.setLong(7, retained.rowVersion());
            if (s.executeUpdate() != 1) throw CourseJdbc.stale("enrollment", retained.enrollmentId());
            return new Enrollment(retained.enrollmentId(), retained.offeringId(), retained.studentId(), requested.enrollmentType(), "ACTIVE", enrolledAt, null, retained.rowVersion() + 1, retained.createdAt(), now);
        } catch (SQLException error) { throw CourseJdbc.failure("reactivate enrollment", error); }
    }

    private static void setEnrollment(PreparedStatement s, Enrollment value) throws SQLException {
        s.setString(1, value.enrollmentId()); s.setString(2, value.offeringId()); s.setString(3, value.studentId()); s.setString(4, value.enrollmentType()); s.setString(5, value.enrollmentStatus()); s.setTimestamp(6, CourseJdbc.timestamp(value.enrolledAt()));
        if (value.droppedAt() == null) s.setNull(7, java.sql.Types.TIMESTAMP); else s.setTimestamp(7, CourseJdbc.timestamp(value.droppedAt()));
        s.setLong(8, value.rowVersion()); s.setTimestamp(9, CourseJdbc.timestamp(value.createdAt())); s.setTimestamp(10, CourseJdbc.timestamp(value.updatedAt()));
    }

    private static Enrollment enrollment(ResultSet r) throws SQLException {
        return new Enrollment(r.getString("enrollmentId"), r.getString("offeringId"), r.getString("studentId"), r.getString("enrollmentType"), r.getString("enrollmentStatus"), CourseJdbc.instant(r, "enrolledAt"), CourseJdbc.instant(r, "droppedAt"), r.getLong("rowVersion"), CourseJdbc.instant(r, "createdAt"), CourseJdbc.instant(r, "updatedAt"));
    }
}
