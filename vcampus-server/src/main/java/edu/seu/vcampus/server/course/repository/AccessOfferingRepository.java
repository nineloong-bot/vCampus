package edu.seu.vcampus.server.course.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JDBC persistence for offerings and their component schedule rows. */
final class AccessOfferingRepository {
    Offering insertOffering(Connection c, Offering offering, List<Schedule> schedules) {
        Instant now = Instant.now();
        Offering saved = new Offering(CourseJdbc.id(offering.offeringId()), offering.termId(), offering.courseId(), offering.teacherUserId(), offering.className(), offering.capacity(), offering.enrolledCount(), offering.offeringStatus(), 0, now, now);
        String sql = "INSERT INTO tblCourseOffering (offeringId, termId, courseId, teacherUserId, className, capacity, enrolledCount, offeringStatus, rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            setOffering(s, saved); s.executeUpdate(); replaceSchedules(c, saved.offeringId(), schedules); return saved;
        } catch (SQLException error) { throw CourseJdbc.failure("insert offering", error); }
    }

    Offering requireOffering(Connection c, String id) {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM tblCourseOffering WHERE offeringId=?")) {
            s.setString(1, id); try (ResultSet r = s.executeQuery()) { if (r.next()) return offering(r); }
        } catch (SQLException error) { throw CourseJdbc.failure("read offering", error); }
        throw CourseJdbc.missing("Offering", id);
    }

    List<Offering> findOfferingsByTerm(Connection c, String termId) {
        List<Offering> values = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM tblCourseOffering WHERE termId=? ORDER BY className")) {
            s.setString(1, termId); try (ResultSet r = s.executeQuery()) { while (r.next()) values.add(offering(r)); }
            return values;
        } catch (SQLException error) { throw CourseJdbc.failure("list offerings", error); }
    }

    OfferingSearchPage searchOfferings(Connection c, OfferingSearchCriteria criteria) {
        SearchSql search = searchSql(criteria);
        long total = count(c, search);
        List<Offering> values = new ArrayList<>();
        int start = criteria.page() * criteria.pageSize();
        try (PreparedStatement s = c.prepareStatement("SELECT o.*" + search.fromWhere() + " ORDER BY o.className, o.offeringId")) {
            bind(s, search.parameters());
            s.setMaxRows(start + criteria.pageSize());
            try (ResultSet r = s.executeQuery()) {
                for (int seen = 0; r.next(); seen++) if (seen >= start) values.add(offering(r));
            }
            return new OfferingSearchPage(values, total);
        } catch (SQLException error) { throw CourseJdbc.failure("search offerings", error); }
    }

    Offering updateOffering(Connection c, Offering offering, long expected, List<Schedule> schedules) {
        Instant now = Instant.now();
        String sql = "UPDATE tblCourseOffering SET termId=?, courseId=?, teacherUserId=?, className=?, capacity=?, enrolledCount=?, offeringStatus=?, rowVersion=?, updatedAt=? WHERE offeringId=? AND rowVersion=?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, offering.termId()); s.setString(2, offering.courseId()); s.setString(3, offering.teacherUserId()); s.setString(4, offering.className()); s.setInt(5, offering.capacity()); s.setInt(6, offering.enrolledCount()); s.setString(7, offering.offeringStatus()); s.setLong(8, expected + 1); s.setTimestamp(9, CourseJdbc.timestamp(now)); s.setString(10, offering.offeringId()); s.setLong(11, expected);
            if (s.executeUpdate() != 1) throw CourseJdbc.stale("offering", offering.offeringId());
            replaceSchedules(c, offering.offeringId(), schedules);
            return new Offering(offering.offeringId(), offering.termId(), offering.courseId(), offering.teacherUserId(), offering.className(), offering.capacity(), offering.enrolledCount(), offering.offeringStatus(), expected + 1, offering.createdAt(), now);
        } catch (SQLException error) { throw CourseJdbc.failure("update offering", error); }
    }

    List<Schedule> findSchedules(Connection c, String offeringId) {
        List<Schedule> values = new ArrayList<>();
        String sql = "SELECT * FROM tblCourseSchedule WHERE offeringId=? ORDER BY dayOfWeek, startWeek, startPeriod";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, offeringId); try (ResultSet r = s.executeQuery()) { while (r.next()) values.add(schedule(r)); }
            return values;
        } catch (SQLException error) { throw CourseJdbc.failure("list schedules", error); }
    }

    private static void setOffering(PreparedStatement s, Offering offering) throws SQLException {
        s.setString(1, offering.offeringId()); s.setString(2, offering.termId()); s.setString(3, offering.courseId()); s.setString(4, offering.teacherUserId()); s.setString(5, offering.className()); s.setInt(6, offering.capacity()); s.setInt(7, offering.enrolledCount()); s.setString(8, offering.offeringStatus()); s.setLong(9, offering.rowVersion()); s.setTimestamp(10, CourseJdbc.timestamp(offering.createdAt())); s.setTimestamp(11, CourseJdbc.timestamp(offering.updatedAt()));
    }

    private static void replaceSchedules(Connection c, String offeringId, List<Schedule> schedules) throws SQLException {
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM tblCourseSchedule WHERE offeringId=?")) {
            delete.setString(1, offeringId); delete.executeUpdate();
        }
        if (schedules.isEmpty()) return;
        String sql = "INSERT INTO tblCourseSchedule (scheduleId, offeringId, dayOfWeek, startPeriod, endPeriod, startWeek, endWeek, classroom) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = c.prepareStatement(sql)) {
            for (Schedule schedule : schedules) {
                insert.setString(1, CourseJdbc.id(schedule.scheduleId())); insert.setString(2, offeringId); insert.setInt(3, schedule.dayOfWeek().getValue()); insert.setInt(4, schedule.startPeriod()); insert.setInt(5, schedule.endPeriod()); insert.setInt(6, schedule.startWeek()); insert.setInt(7, schedule.endWeek()); insert.setString(8, schedule.classroom()); insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static Offering offering(ResultSet r) throws SQLException {
        return new Offering(r.getString("offeringId"), r.getString("termId"), r.getString("courseId"), r.getString("teacherUserId"), r.getString("className"), r.getInt("capacity"), r.getInt("enrolledCount"), r.getString("offeringStatus"), r.getLong("rowVersion"), CourseJdbc.instant(r, "createdAt"), CourseJdbc.instant(r, "updatedAt"));
    }

    private static Schedule schedule(ResultSet r) throws SQLException {
        return new Schedule(r.getString("scheduleId"), r.getString("offeringId"), DayOfWeek.of(r.getInt("dayOfWeek")), r.getInt("startPeriod"), r.getInt("endPeriod"), r.getInt("startWeek"), r.getInt("endWeek"), r.getString("classroom"));
    }

    private static long count(Connection c, SearchSql search) {
        try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*)" + search.fromWhere())) {
            bind(s, search.parameters()); try (ResultSet r = s.executeQuery()) { return r.next() ? r.getLong(1) : 0; }
        } catch (SQLException error) { throw CourseJdbc.failure("count offerings", error); }
    }

    private static SearchSql searchSql(OfferingSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder(" FROM tblCourseOffering o INNER JOIN tblCourse c ON o.courseId=c.courseId WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (criteria.termId() != null && !criteria.termId().isBlank()) { sql.append(" AND o.termId=?"); parameters.add(criteria.termId()); }
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            sql.append(" AND (o.className LIKE ? OR c.courseCode LIKE ? OR c.courseName LIKE ?)");
            String pattern = "%" + criteria.keyword().trim() + "%";
            parameters.add(pattern); parameters.add(pattern); parameters.add(pattern);
        }
        if (criteria.dayOfWeek() != null) { sql.append(" AND EXISTS (SELECT * FROM tblCourseSchedule s WHERE s.offeringId=o.offeringId AND s.dayOfWeek=?)"); parameters.add(criteria.dayOfWeek().getValue()); }
        if (criteria.availableOnly()) sql.append(" AND o.offeringStatus='OPEN' AND o.enrolledCount < o.capacity");
        return new SearchSql(sql.toString(), parameters);
    }

    private static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            Object value = parameters.get(i);
            if (value instanceof Integer integer) statement.setInt(i + 1, integer); else statement.setString(i + 1, (String) value);
        }
    }

    private record SearchSql(String fromWhere, List<Object> parameters) {
    }
}
