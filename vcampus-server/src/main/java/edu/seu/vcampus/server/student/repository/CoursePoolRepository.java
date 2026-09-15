package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.CoursePoolItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Provides course pool repository behavior. */
public final class CoursePoolRepository {

    /**
     * Performs the list courses operation.
     * @param connection the connection
     * @param departmentId the department identifier
     * @param keyword the keyword
     * @return the operation result
     */
    public List<CoursePoolItem> listCourses(Connection connection, String departmentId, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT courseId, courseCode, courseName, departmentId, departmentName, "
                + "credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt "
                + "FROM tblCourse WHERE isActive = TRUE ");
        List<String> params = new ArrayList<>();
        if (departmentId != null && !departmentId.isBlank()) {
            sql.append("AND departmentId = ? ");
            params.add(departmentId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (courseCode LIKE ? OR courseName LIKE ?) ");
            String pat = "%" + keyword.trim() + "%";
            params.add(pat);
            params.add(pat);
        }
        sql.append("ORDER BY departmentName, courseCode");

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<CoursePoolItem> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return List.copyOf(list);
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot list course pool", e);
        }
    }

    /**
     * Performs the find by identifier operation.
     * @param connection the connection
     * @param courseId the course identifier
     * @return the operation result
     */
    public Optional<CoursePoolItem> findById(Connection connection, String courseId) {
        String sql = "SELECT courseId, courseCode, courseName, departmentId, departmentName, "
                + "credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt "
                + "FROM tblCourse WHERE courseId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot find course in pool by id", e);
        }
    }

    /**
     * Performs the find by code operation.
     * @param connection the connection
     * @param courseCode the course code
     * @return the operation result
     */
    public Optional<CoursePoolItem> findByCode(Connection connection, String courseCode) {
        String sql = "SELECT courseId, courseCode, courseName, departmentId, departmentName, "
                + "credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt "
                + "FROM tblCourse WHERE courseCode = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, courseCode);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot find course in pool by code", e);
        }
    }

    /**
     * Performs the insert operation.
     * @param connection the connection
     * @param item the item
     */
    public void insert(Connection connection, CoursePoolItem item) {
        String sql = "INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, "
                + "credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, item.courseId());
            stmt.setString(2, item.courseCode());
            stmt.setString(3, item.courseName());
            stmt.setString(4, item.departmentId());
            stmt.setString(5, item.departmentName());
            stmt.setBigDecimal(6, item.credit());
            stmt.setInt(7, item.totalHours());
            stmt.setString(8, item.description());
            stmt.setBoolean(9, item.active());
            stmt.setLong(10, item.rowVersion());
            stmt.setTimestamp(11, Timestamp.from(item.createdAt()));
            stmt.setTimestamp(12, Timestamp.from(item.updatedAt()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new OrganizationPersistenceException("Cannot insert course pool item", e);
        }
    }

    private CoursePoolItem map(ResultSet rs) throws SQLException {
        return new CoursePoolItem(
                rs.getString("courseId"),
                rs.getString("courseCode"),
                rs.getString("courseName"),
                rs.getString("departmentId"),
                rs.getString("departmentName"),
                rs.getBigDecimal("credit"),
                rs.getInt("totalHours"),
                rs.getString("description"),
                rs.getBoolean("isActive"),
                rs.getLong("rowVersion"),
                rs.getTimestamp("createdAt").toInstant(),
                rs.getTimestamp("updatedAt").toInstant());
    }
}
