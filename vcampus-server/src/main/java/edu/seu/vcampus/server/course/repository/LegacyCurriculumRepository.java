package edu.seu.vcampus.server.course.repository;

import edu.seu.vcampus.common.course.AcademicSeason;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Compatibility adapter used only by the standalone course demo database. */
final class LegacyCurriculumRepository implements CurriculumRepository {
    @Override public CurriculumPlan insertPlan(Connection c, CurriculumPlan plan) {
        ensureSchema(c);
        String sql = "INSERT INTO tblCurriculumPlan "
                + "(planId,majorCode,cohortYear,planName,planVersion,planStatus) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, plan.planId()); s.setString(2, plan.majorCode());
            s.setInt(3, plan.cohortYear()); s.setString(4, plan.planName());
            s.setInt(5, plan.planVersion()); s.setString(6, plan.planStatus());
            s.executeUpdate(); return plan;
        } catch (SQLException e) { throw CourseJdbc.failure("insert legacy curriculum plan", e); }
    }

    @Override public CurriculumCourse insertCourse(Connection c, CurriculumCourse course) {
        ensureSchema(c);
        String sql = "INSERT INTO tblCurriculumCourse "
                + "(planCourseId,planId,courseId,academicYearNo,season,courseNature,"
                + "courseCategory,offeringUnit) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, course.planCourseId()); s.setString(2, course.planId());
            s.setString(3, course.courseId()); s.setInt(4, course.academicYearNo());
            s.setString(5, course.season().name()); s.setString(6, course.courseNature());
            s.setString(7, course.courseCategory()); s.setString(8, course.offeringUnit());
            s.executeUpdate(); return course;
        } catch (SQLException e) { throw CourseJdbc.failure("insert legacy curriculum course", e); }
    }

    @Override public void insertPrerequisite(Connection c, String id, String planId,
                                               String courseId, String prerequisiteCourseId) {
        ensureSchema(c);
        String sql = "INSERT INTO tblCurriculumPrerequisite "
                + "(prerequisiteId,planId,courseId,prerequisiteCourseId) VALUES (?,?,?,?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, id); s.setString(2, planId); s.setString(3, courseId);
            s.setString(4, prerequisiteCourseId); s.executeUpdate();
        } catch (SQLException e) { throw CourseJdbc.failure("insert legacy prerequisite", e); }
    }

    @Override public Optional<CurriculumPlan> findPublishedPlan(
            Connection c, String majorCode, int cohortYear) {
        ensureSchema(c);
        String sql = "SELECT * FROM tblCurriculumPlan WHERE majorCode=? AND cohortYear=? "
                + "AND planStatus='PUBLISHED' ORDER BY planVersion DESC";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, majorCode); s.setInt(2, cohortYear);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(new CurriculumPlan(r.getString("planId"),
                        r.getString("majorCode"), r.getInt("cohortYear"), r.getString("planName"),
                        r.getInt("planVersion"), r.getString("planStatus"))) : Optional.empty();
            }
        } catch (SQLException e) { throw CourseJdbc.failure("find legacy curriculum plan", e); }
    }

    @Override public List<CurriculumCourse> findScheduledCourses(
            Connection c, String planId, int year, AcademicSeason season) {
        return findCourses(c, planId).stream().filter(course -> course.academicYearNo() == year
                && course.season() == season).toList();
    }

    @Override public List<CurriculumCourse> findEarlierCourses(
            Connection c, String planId, int year, AcademicSeason season) {
        int current = (year - 1) * 3 + season.curriculumTermOrdinal();
        return findCourses(c, planId).stream().filter(course ->
                (course.academicYearNo() - 1) * 3
                        + course.season().curriculumTermOrdinal() < current).toList();
    }

    @Override public Set<String> findPrerequisiteCourseIds(
            Connection c, String planId, String courseId) {
        ensureSchema(c);
        Set<String> ids = new LinkedHashSet<>();
        String sql = "SELECT prerequisiteCourseId FROM tblCurriculumPrerequisite "
                + "WHERE planId=? AND courseId=? ORDER BY prerequisiteCourseId";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, planId); s.setString(2, courseId);
            try (ResultSet r = s.executeQuery()) { while (r.next()) ids.add(r.getString(1)); }
            return Set.copyOf(ids);
        } catch (SQLException e) { throw CourseJdbc.failure("find legacy prerequisites", e); }
    }

    private List<CurriculumCourse> findCourses(Connection c, String planId) {
        ensureSchema(c);
        List<CurriculumCourse> values = new ArrayList<>();
        String sql = "SELECT * FROM tblCurriculumCourse WHERE planId=? ORDER BY academicYearNo";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, planId);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) values.add(new CurriculumCourse(r.getString("planCourseId"),
                        r.getString("planId"), r.getString("courseId"), r.getInt("academicYearNo"),
                        AcademicSeason.valueOf(r.getString("season")), r.getString("courseNature"),
                        r.getString("courseCategory"), r.getString("offeringUnit")));
            }
            return List.copyOf(values);
        } catch (SQLException e) { throw CourseJdbc.failure("find legacy curriculum courses", e); }
    }

    private static void ensureSchema(Connection connection) {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, null, "tblCurriculumPlan", new String[]{"TABLE"})) {
            if (tables.next()) return;
        } catch (SQLException error) {
            throw CourseJdbc.failure("inspect legacy curriculum schema", error);
        }
        List<String> statements = List.of(
                "CREATE TABLE tblCurriculumPlan (planId VARCHAR(36) PRIMARY KEY," +
                        "majorCode VARCHAR(16),cohortYear LONG,planName VARCHAR(128)," +
                        "planVersion LONG,planStatus VARCHAR(16))",
                "CREATE TABLE tblCurriculumCourse (planCourseId VARCHAR(36) PRIMARY KEY," +
                        "planId VARCHAR(36),courseId VARCHAR(36),academicYearNo LONG," +
                        "season VARCHAR(16),courseNature VARCHAR(16),courseCategory VARCHAR(64)," +
                        "offeringUnit VARCHAR(64))",
                "CREATE TABLE tblCurriculumPrerequisite (prerequisiteId VARCHAR(36) PRIMARY KEY," +
                        "planId VARCHAR(36),courseId VARCHAR(36),prerequisiteCourseId VARCHAR(36))");
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) statement.execute(sql);
        } catch (SQLException error) {
            throw CourseJdbc.failure("create standalone curriculum schema", error);
        }
    }
}
