package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.common.student.GradeResult;
import edu.seu.vcampus.common.student.StudentGradeView;
import edu.seu.vcampus.server.student.domain.StudentGrade;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

/** Stores student grades inside caller-owned transactions. */
public final class StudentGradeRepository {

    public Optional<StudentGrade> findByStudentAndCourse(Connection connection,
            String studentId, String planCourseId) {
        String sql = "SELECT gradeId, studentId, planCourseId, result, recordedSemester, "
                + "operatorUserId, rowVersion, createdAt, updatedAt "
                + "FROM tblStudentGrade WHERE studentId = ? AND planCourseId = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            statement.setString(2, planCourseId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot read student grade", error);
        }
    }

    public void insert(Connection connection, StudentGrade grade) {
        String sql = "INSERT INTO tblStudentGrade (gradeId, studentId, planCourseId, result, "
                + "recordedSemester, operatorUserId, rowVersion, createdAt, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, grade.gradeId());
            statement.setString(2, grade.studentId());
            statement.setString(3, grade.planCourseId());
            statement.setString(4, grade.result().name());
            statement.setString(5, grade.recordedSemester());
            statement.setString(6, grade.operatorUserId());
            statement.setLong(7, grade.rowVersion());
            statement.setTimestamp(8, Timestamp.from(grade.createdAt()));
            statement.setTimestamp(9, Timestamp.from(grade.updatedAt()));
            statement.executeUpdate();
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot insert student grade", error);
        }
    }

    public void update(Connection connection, StudentGrade grade, long expectedVersion) {
        String sql = "UPDATE tblStudentGrade SET result=?, recordedSemester=?, "
                + "operatorUserId=?, rowVersion=rowVersion+1, updatedAt=? "
                + "WHERE gradeId=? AND rowVersion=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, grade.result().name());
            statement.setString(2, grade.recordedSemester());
            statement.setString(3, grade.operatorUserId());
            statement.setTimestamp(4, Timestamp.from(grade.updatedAt()));
            statement.setString(5, grade.gradeId());
            statement.setLong(6, expectedVersion);
            if (statement.executeUpdate() != 1)
                throw new ConcurrentModificationException("Grade version changed");
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot update student grade", error);
        }
    }

    public List<StudentGradeView> listByStudent(Connection connection, String studentId) {
        String sql = "SELECT g.gradeId, g.studentId, g.planCourseId, c.courseCode, c.courseName, "
                + "c.credits, c.courseType, c.semester, g.result, g.recordedSemester, g.rowVersion "
                + "FROM tblStudentGrade g INNER JOIN tblTrainingPlanCourse c "
                + "ON g.planCourseId = c.planCourseId "
                + "WHERE g.studentId = ? ORDER BY c.courseType, c.semester, c.courseCode";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (var result = statement.executeQuery()) {
                List<StudentGradeView> values = new ArrayList<>();
                while (result.next()) values.add(mapView(result));
                return List.copyOf(values);
            }
        } catch (java.sql.SQLException error) {
            throw new OrganizationPersistenceException("Cannot list student grades", error);
        }
    }

    private StudentGrade map(java.sql.ResultSet result) throws java.sql.SQLException {
        return new StudentGrade(
                result.getString("gradeId"),
                result.getString("studentId"),
                result.getString("planCourseId"),
                GradeResult.valueOf(result.getString("result")),
                result.getString("recordedSemester"),
                result.getString("operatorUserId"),
                result.getLong("rowVersion"),
                result.getTimestamp("createdAt").toInstant(),
                result.getTimestamp("updatedAt").toInstant());
    }

    private StudentGradeView mapView(java.sql.ResultSet result) throws java.sql.SQLException {
        return new StudentGradeView(
                result.getString("gradeId"),
                result.getString("studentId"),
                result.getString("planCourseId"),
                result.getString("courseCode"),
                result.getString("courseName"),
                result.getBigDecimal("credits"),
                edu.seu.vcampus.common.student.CourseType.valueOf(result.getString("courseType")),
                result.getInt("semester"),
                GradeResult.valueOf(result.getString("result")),
                result.getString("recordedSemester"),
                result.getLong("rowVersion"));
    }
}
