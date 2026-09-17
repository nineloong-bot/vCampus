package edu.seu.vcampus.server.course.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access for classroom capacity and occupancy. */
final class AccessClassroomRepository {
    List<Classroom> search(Connection connection, String keyword, int minimumCapacity, int limit) {
        List<Classroom> values = new ArrayList<>();
        String sql = "SELECT * FROM tblClassroom WHERE isActive=TRUE AND capacity>=? AND classroom LIKE ? ORDER BY classroom";
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
            try (var count = connection.createStatement().executeQuery("SELECT COUNT(*) FROM tblClassroom")) {
                if (count.next() && count.getInt(1) == 0) {
                    return Optional.of(new Classroom(classroom, Integer.MAX_VALUE, true));
                }
            }
            return Optional.empty();
        } catch (SQLException error) { throw CourseJdbc.failure("read classroom", error); }
    }

    List<Schedule> schedules(Connection connection, String termId, String classroom, String excluded) {
        List<Schedule> values = new ArrayList<>();
        String sql = "SELECT s.* FROM tblCourseSchedule s INNER JOIN tblCourseOffering o "
                + "ON s.offeringId=o.offeringId WHERE o.termId=? AND s.classroom=?"
                + (excluded == null ? "" : " AND o.offeringId<>?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, termId); statement.setString(2, classroom);
            if (excluded != null) statement.setString(3, excluded);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new Schedule(result.getString("scheduleId"),
                        result.getString("offeringId"), DayOfWeek.of(result.getInt("dayOfWeek")),
                        result.getInt("startPeriod"), result.getInt("endPeriod"),
                        result.getInt("startWeek"), result.getInt("endWeek"), classroom));
            }
            return values;
        } catch (SQLException error) { throw CourseJdbc.failure("read classroom schedules", error); }
    }

    private static Classroom map(ResultSet result) throws SQLException {
        return new Classroom(result.getString("classroom"), result.getInt("capacity"),
                result.getBoolean("isActive"));
    }
}
