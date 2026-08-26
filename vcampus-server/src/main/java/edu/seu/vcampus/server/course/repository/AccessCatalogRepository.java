package edu.seu.vcampus.server.course.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JDBC persistence for course catalog and term rows. */
final class AccessCatalogRepository {
    Term insertTerm(Connection c, Term term) {
        Instant now = Instant.now();
        Term saved = new Term(CourseJdbc.id(term.termId()), term.termCode(), term.termName(), term.startDate(),
                term.endDate(), term.enrollmentStartAt(), term.enrollmentEndAt(), term.adjustmentStartAt(),
                term.adjustmentEndAt(), term.termStatus(), 0, now, now);
        String sql = "INSERT INTO tblTerm (termId, termCode, termName, startDate, endDate, enrollmentStartAt, enrollmentEndAt, adjustmentStartAt, adjustmentEndAt, termStatus, rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, saved.termId()); s.setString(2, saved.termCode()); s.setString(3, saved.termName());
            s.setDate(4, java.sql.Date.valueOf(saved.startDate())); s.setDate(5, java.sql.Date.valueOf(saved.endDate()));
            s.setTimestamp(6, CourseJdbc.timestamp(saved.enrollmentStartAt())); s.setTimestamp(7, CourseJdbc.timestamp(saved.enrollmentEndAt()));
            s.setTimestamp(8, CourseJdbc.timestamp(saved.adjustmentStartAt())); s.setTimestamp(9, CourseJdbc.timestamp(saved.adjustmentEndAt()));
            s.setString(10, saved.termStatus()); s.setLong(11, 0); s.setTimestamp(12, CourseJdbc.timestamp(now)); s.setTimestamp(13, CourseJdbc.timestamp(now));
            s.executeUpdate(); return saved;
        } catch (SQLException error) { throw CourseJdbc.failure("insert term", error); }
    }

    Term requireTerm(Connection c, String id) {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM tblTerm WHERE termId = ?")) {
            s.setString(1, id); try (ResultSet r = s.executeQuery()) { if (r.next()) return term(r); }
        } catch (SQLException error) { throw CourseJdbc.failure("read term", error); }
        throw CourseJdbc.missing("Term", id);
    }

    List<Term> findTerms(Connection c) {
        List<Term> values = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM tblTerm ORDER BY termCode DESC"); ResultSet r = s.executeQuery()) {
            while (r.next()) values.add(term(r)); return values;
        } catch (SQLException error) { throw CourseJdbc.failure("list terms", error); }
    }

    Term updateTerm(Connection c, Term term, long expected) {
        Instant now = Instant.now();
        String sql = "UPDATE tblTerm SET termCode=?, termName=?, startDate=?, endDate=?, enrollmentStartAt=?, enrollmentEndAt=?, adjustmentStartAt=?, adjustmentEndAt=?, termStatus=?, rowVersion=?, updatedAt=? WHERE termId=? AND rowVersion=?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, term.termCode()); s.setString(2, term.termName()); s.setDate(3, java.sql.Date.valueOf(term.startDate())); s.setDate(4, java.sql.Date.valueOf(term.endDate()));
            s.setTimestamp(5, CourseJdbc.timestamp(term.enrollmentStartAt())); s.setTimestamp(6, CourseJdbc.timestamp(term.enrollmentEndAt())); s.setTimestamp(7, CourseJdbc.timestamp(term.adjustmentStartAt())); s.setTimestamp(8, CourseJdbc.timestamp(term.adjustmentEndAt()));
            s.setString(9, term.termStatus()); s.setLong(10, expected + 1); s.setTimestamp(11, CourseJdbc.timestamp(now)); s.setString(12, term.termId()); s.setLong(13, expected);
            if (s.executeUpdate() != 1) throw CourseJdbc.stale("term", term.termId());
            return new Term(term.termId(), term.termCode(), term.termName(), term.startDate(), term.endDate(), term.enrollmentStartAt(), term.enrollmentEndAt(), term.adjustmentStartAt(), term.adjustmentEndAt(), term.termStatus(), expected + 1, term.createdAt(), now);
        } catch (SQLException error) { throw CourseJdbc.failure("update term", error); }
    }

    Course insertCourse(Connection c, Course course) {
        Instant now = Instant.now(); Course saved = new Course(CourseJdbc.id(course.courseId()), course.courseCode(), course.courseName(), course.credit(), course.totalHours(), course.description(), course.active(), 0, now, now);
        String sql = "INSERT INTO tblCourse (courseId, courseCode, courseName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, saved.courseId()); s.setString(2, saved.courseCode()); s.setString(3, saved.courseName()); s.setBigDecimal(4, saved.credit()); s.setInt(5, saved.totalHours()); s.setString(6, saved.description()); s.setBoolean(7, saved.active()); s.setLong(8, 0); s.setTimestamp(9, CourseJdbc.timestamp(now)); s.setTimestamp(10, CourseJdbc.timestamp(now)); s.executeUpdate(); return saved;
        } catch (SQLException error) { throw CourseJdbc.failure("insert course", error); }
    }

    Course requireCourse(Connection c, String id) {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM tblCourse WHERE courseId = ?")) {
            s.setString(1, id); try (ResultSet r = s.executeQuery()) { if (r.next()) return course(r); }
        } catch (SQLException error) { throw CourseJdbc.failure("read course", error); }
        throw CourseJdbc.missing("Course", id);
    }

    List<Course> findCourses(Connection c) {
        List<Course> values = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM tblCourse ORDER BY courseCode"); ResultSet r = s.executeQuery()) {
            while (r.next()) values.add(course(r)); return values;
        } catch (SQLException error) { throw CourseJdbc.failure("list courses", error); }
    }

    Course updateCourse(Connection c, Course course, long expected) {
        Instant now = Instant.now();
        String sql = "UPDATE tblCourse SET courseCode=?, courseName=?, credit=?, totalHours=?, description=?, isActive=?, rowVersion=?, updatedAt=? WHERE courseId=? AND rowVersion=?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, course.courseCode()); s.setString(2, course.courseName()); s.setBigDecimal(3, course.credit()); s.setInt(4, course.totalHours()); s.setString(5, course.description()); s.setBoolean(6, course.active()); s.setLong(7, expected + 1); s.setTimestamp(8, CourseJdbc.timestamp(now)); s.setString(9, course.courseId()); s.setLong(10, expected);
            if (s.executeUpdate() != 1) throw CourseJdbc.stale("course", course.courseId());
            return new Course(course.courseId(), course.courseCode(), course.courseName(), course.credit(), course.totalHours(), course.description(), course.active(), expected + 1, course.createdAt(), now);
        } catch (SQLException error) { throw CourseJdbc.failure("update course", error); }
    }

    private static Term term(ResultSet r) throws SQLException {
        return new Term(r.getString("termId"), r.getString("termCode"), r.getString("termName"), r.getDate("startDate").toLocalDate(), r.getDate("endDate").toLocalDate(), CourseJdbc.instant(r, "enrollmentStartAt"), CourseJdbc.instant(r, "enrollmentEndAt"), CourseJdbc.instant(r, "adjustmentStartAt"), CourseJdbc.instant(r, "adjustmentEndAt"), r.getString("termStatus"), r.getLong("rowVersion"), CourseJdbc.instant(r, "createdAt"), CourseJdbc.instant(r, "updatedAt"));
    }

    private static Course course(ResultSet r) throws SQLException {
        return new Course(r.getString("courseId"), r.getString("courseCode"), r.getString("courseName"), r.getBigDecimal("credit"), r.getInt("totalHours"), r.getString("description"), r.getBoolean("isActive"), r.getLong("rowVersion"), CourseJdbc.instant(r, "createdAt"), CourseJdbc.instant(r, "updatedAt"));
    }
}
