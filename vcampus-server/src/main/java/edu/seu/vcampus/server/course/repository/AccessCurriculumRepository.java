package edu.seu.vcampus.server.course.repository;

import edu.seu.vcampus.common.course.AcademicSeason;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Curriculum port backed by the student module's canonical training-plan tables. */
public final class AccessCurriculumRepository implements CurriculumRepository {
    private final CurriculumRepository legacy = new LegacyCurriculumRepository();

    @Override
    public CurriculumPlan insertPlan(Connection c, CurriculumPlan plan) {
        if (!hasCanonicalTables(c)) return legacy.insertPlan(c, plan);
        String sql = "INSERT INTO tblTrainingPlan (planId,majorId,enrollmentYear,planName,"
                + "minElectiveCount,minElectiveCredits,isActive,rowVersion,createdAt,updatedAt) "
                + "SELECT ?,majorId,?,?,0,0,?,?,NOW(),NOW() FROM tblMajor WHERE majorCode=?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, plan.planId()); s.setInt(2, plan.cohortYear());
            s.setString(3, plan.planName()); s.setBoolean(4, "PUBLISHED".equals(plan.planStatus()));
            s.setInt(5, plan.planVersion()); s.setString(6, plan.majorCode());
            if (s.executeUpdate() != 1) throw new SQLException("major code not found: " + plan.majorCode());
            return plan;
        } catch (SQLException e) { throw CourseJdbc.failure("insert curriculum plan", e); }
    }

    @Override
    public CurriculumCourse insertCourse(Connection c, CurriculumCourse course) {
        if (!hasCanonicalTables(c)) return legacy.insertCourse(c, course);
        String sql = "INSERT INTO tblTrainingPlanCourse (planCourseId,planId,courseCode,courseName,"
                + "credits,courseType,semester,courseNature,courseCategory,offeringUnit,isActive,"
                + "rowVersion,createdAt,updatedAt) SELECT ?,?,courseCode,courseName,credit,?,?,?,?,?,"
                + "TRUE,0,NOW(),NOW() FROM tblCourse WHERE courseId=?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, course.planCourseId()); s.setString(2, course.planId());
            s.setString(3, "ELECTIVE".equals(course.courseNature()) ? "ELECTIVE" : "REQUIRED");
            s.setInt(4, ordinal(course.academicYearNo(), course.season()));
            s.setString(5, course.courseNature()); s.setString(6, course.courseCategory());
            s.setString(7, course.offeringUnit()); s.setString(8, course.courseId());
            if (s.executeUpdate() != 1) throw new SQLException("course not found: " + course.courseId());
            return course;
        } catch (SQLException e) { throw CourseJdbc.failure("insert curriculum course", e); }
    }

    @Override
    public void insertPrerequisite(Connection c, String id, String planId,
                                   String courseId, String prerequisiteCourseId) {
        if (!hasCanonicalTables(c)) {
            legacy.insertPrerequisite(c, id, planId, courseId, prerequisiteCourseId); return;
        }
        String sql = "INSERT INTO tblTrainingPlanPrerequisite "
                + "(prerequisiteId,planId,courseId,prerequisiteCourseId) VALUES (?,?,?,?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, id); s.setString(2, planId); s.setString(3, courseId);
            s.setString(4, prerequisiteCourseId); s.executeUpdate();
        } catch (SQLException e) { throw CourseJdbc.failure("insert curriculum prerequisite", e); }
    }

    @Override
    public Optional<CurriculumPlan> findPublishedPlan(Connection c, String majorCode, int cohortYear) {
        if (!hasCanonicalTables(c)) return legacy.findPublishedPlan(c, majorCode, cohortYear);
        String sql = "SELECT p.planId,m.majorCode,p.enrollmentYear,p.planName,p.rowVersion "
                + "FROM tblTrainingPlan p INNER JOIN tblMajor m ON p.majorId=m.majorId "
                + "WHERE m.majorCode=? AND p.enrollmentYear=? AND p.isActive=TRUE ORDER BY p.rowVersion DESC";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, majorCode); s.setInt(2, cohortYear);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(plan(r)) : Optional.empty(); }
        } catch (SQLException e) { throw CourseJdbc.failure("find curriculum plan", e); }
    }

    @Override
    public List<CurriculumCourse> findScheduledCourses(Connection c, String planId,
                                                       int academicYearNo, AcademicSeason season) {
        if (!hasCanonicalTables(c)) return legacy.findScheduledCourses(c, planId, academicYearNo, season);
        return findCourses(c, planId).stream().filter(course -> course.academicYearNo() == academicYearNo
                && course.season() == season).toList();
    }

    @Override
    public List<CurriculumCourse> findEarlierCourses(Connection c, String planId,
                                                     int academicYearNo, AcademicSeason season) {
        if (!hasCanonicalTables(c)) return legacy.findEarlierCourses(c, planId, academicYearNo, season);
        int current = ordinal(academicYearNo, season);
        return findCourses(c, planId).stream()
                .filter(course -> ordinal(course.academicYearNo(), course.season()) < current).toList();
    }

    @Override
    public Set<String> findPrerequisiteCourseIds(Connection c, String planId, String courseId) {
        if (!hasCanonicalTables(c)) return legacy.findPrerequisiteCourseIds(c, planId, courseId);
        Set<String> ids = new LinkedHashSet<>();
        String sql = "SELECT prerequisiteCourseId FROM tblTrainingPlanPrerequisite "
                + "WHERE planId=? AND courseId=? ORDER BY prerequisiteCourseId";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, planId); s.setString(2, courseId);
            try (ResultSet r = s.executeQuery()) { while (r.next()) ids.add(r.getString(1)); }
            return Set.copyOf(ids);
        } catch (SQLException e) { throw CourseJdbc.failure("find curriculum prerequisites", e); }
    }

    private List<CurriculumCourse> findCourses(Connection c, String planId) {
        List<CurriculumCourse> values = new ArrayList<>();
        String sql = "SELECT pc.planCourseId,pc.planId,c.courseId,pc.semester,pc.courseType,"
                + "pc.courseNature,pc.courseCategory,pc.offeringUnit FROM tblTrainingPlanCourse pc "
                + "INNER JOIN tblCourse c ON pc.courseCode=c.courseCode "
                + "WHERE pc.planId=? AND pc.isActive=TRUE ORDER BY pc.semester,c.courseCode";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, planId);
            try (ResultSet r = s.executeQuery()) { while (r.next()) values.add(course(r)); }
            return List.copyOf(values);
        } catch (SQLException e) { throw CourseJdbc.failure("find curriculum courses", e); }
    }

    private static CurriculumPlan plan(ResultSet r) throws SQLException {
        return new CurriculumPlan(r.getString("planId"), r.getString("majorCode"),
                r.getInt("enrollmentYear"), r.getString("planName"), r.getInt("rowVersion"),
                "PUBLISHED");
    }

    private static CurriculumCourse course(ResultSet r) throws SQLException {
        int semester = r.getInt("semester");
        String nature = r.getString("courseNature");
        if (nature == null) nature = r.getString("courseType");
        String category = r.getString("courseCategory");
        String unit = r.getString("offeringUnit");
        return new CurriculumCourse(r.getString("planCourseId"), r.getString("planId"),
                r.getString("courseId"), (semester - 1) / 3 + 1, season(semester), nature,
                category == null ? "培养方案课程" : category,
                unit == null ? "专业所在院系" : unit);
    }

    private static int ordinal(int academicYearNo, AcademicSeason season) {
        return (academicYearNo - 1) * 3 + season.curriculumTermOrdinal();
    }

    private static AcademicSeason season(int semester) {
        return switch ((semester - 1) % 3 + 1) {
            case 1 -> AcademicSeason.SUMMER;
            case 2 -> AcademicSeason.AUTUMN;
            default -> AcademicSeason.SPRING;
        };
    }

    private static boolean hasCanonicalTables(Connection connection) {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, null, "tblTrainingPlan", new String[]{"TABLE"})) {
            return tables.next();
        } catch (SQLException error) {
            throw CourseJdbc.failure("inspect training-plan schema", error);
        }
    }
}
