package edu.seu.vcampus.server.course.repository;

import java.sql.*;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access for classroom inventory and offering occupancy. */
final class AccessClassroomRepository {
    List<Classroom> search(Connection connection, String keyword, int minimumCapacity, int limit) {
        List<Classroom> values = new ArrayList<>();
        String sql = "SELECT * FROM tblClassroom WHERE isActive=TRUE "
                + "AND (capacity>=? OR sharedSportsVenue=TRUE) AND classroom LIKE ? ORDER BY classroom";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, minimumCapacity);
            statement.setString(2, "%" + (keyword == null ? "" : keyword.strip()) + "%");
            statement.setMaxRows(limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(map(result));
            }
            return values;
        } catch (SQLException error) { throw CourseJdbc.failure("search classrooms", error); }
    }

    Optional<Classroom> find(Connection connection, String classroom) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblClassroom WHERE classroom=?")) {
            statement.setString(1, classroom);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return Optional.of(map(result));
            }
            try (ResultSet count = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM tblClassroom")) {
                if (count.next() && count.getInt(1) == 0) {
                    return Optional.of(new Classroom(classroom, Integer.MAX_VALUE, true, false));
                }
            }
            return Optional.empty();
        } catch (SQLException error) { throw CourseJdbc.failure("read classroom", error); }
    }

    List<Schedule> roomSchedules(Connection c, String termId, String room, String excluded) {
        return schedules(c, "s.classroom=?", termId, room, excluded);
    }

    List<Schedule> teacherSchedules(Connection c, String termId, String teacher, String excluded) {
        return schedules(c, "o.teacherUserId=?", termId, teacher, excluded);
    }

    private List<Schedule> schedules(Connection connection, String filter, String termId,
                                     String value, String excluded) {
        List<Schedule> values = new ArrayList<>();
        String sql = "SELECT s.* FROM tblCourseSchedule s INNER JOIN tblCourseOffering o "
                + "ON s.offeringId=o.offeringId WHERE o.termId=? AND " + filter
                + (excluded == null ? "" : " AND o.offeringId<>?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, termId); statement.setString(2, value);
            if (excluded != null) statement.setString(3, excluded);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(schedule(result));
            }
            return values;
        } catch (SQLException error) { throw CourseJdbc.failure("read scheduling occupancy", error); }
    }

    private static Classroom map(ResultSet result) throws SQLException {
        return new Classroom(result.getString("classroom"), result.getInt("capacity"),
                result.getBoolean("isActive"), result.getBoolean("sharedSportsVenue"));
    }

    private static Schedule schedule(ResultSet result) throws SQLException {
        return new Schedule(result.getString("scheduleId"), result.getString("offeringId"),
                DayOfWeek.of(result.getInt("dayOfWeek")), result.getInt("startPeriod"),
                result.getInt("endPeriod"), result.getInt("startWeek"),
                result.getInt("endWeek"), result.getString("classroom"));
    }
}
