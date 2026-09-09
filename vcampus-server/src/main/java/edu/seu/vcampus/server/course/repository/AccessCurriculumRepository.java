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

/** Microsoft Access implementation of the temporary curriculum read model. */
public final class AccessCurriculumRepository implements CurriculumRepository {
    @Override
    public CurriculumPlan insertPlan(Connection c, CurriculumPlan plan) {
        String sql = "INSERT INTO tblCurriculumPlan (planId,majorCode,cohortYear,planName,planVersion,planStatus) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, plan.planId()); s.setString(2, plan.majorCode());
            s.setInt(3, plan.cohortYear()); s.setString(4, plan.planName());
            s.setInt(5, plan.planVersion()); s.setString(6, plan.planStatus());
            s.executeUpdate(); return plan;
        } catch (SQLException e) { throw CourseJdbc.failure("insert curriculum plan", e); }
    }

    @Override
    public CurriculumCourse insertCourse(Connection c, CurriculumCourse course) {
        String sql = "INSERT INTO tblCurriculumCourse (planCourseId,planId,courseId,academicYearNo,season,courseNature,courseCategory,offeringUnit) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, course.planCourseId()); s.setString(2, course.planId());
            s.setString(3, course.courseId()); s.setInt(4, course.academicYearNo());
            s.setString(5, course.season().name()); s.setString(6, course.courseNature());
            s.setString(7, course.courseCategory()); s.setString(8, course.offeringUnit());
            s.executeUpdate(); return course;
        } catch (SQLException e) { throw CourseJdbc.failure("insert curriculum course", e); }
    }

    @Override
    public void insertPrerequisite(Connection c, String id, String planId,
                                   String courseId, String prerequisiteCourseId) {
        String sql = "INSERT INTO tblCurriculumPrerequisite (prerequisiteId,planId,courseId,prerequisiteCourseId) VALUES (?,?,?,?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, id); s.setString(2, planId); s.setString(3, courseId);
            s.setString(4, prerequisiteCourseId); s.executeUpdate();
        } catch (SQLException e) { throw CourseJdbc.failure("insert curriculum prerequisite", e); }
    }

    @Override
    public Optional<CurriculumPlan> findPublishedPlan(Connection c, String majorCode, int cohortYear) {
        String sql = "SELECT * FROM tblCurriculumPlan WHERE majorCode=? AND cohortYear=? AND planStatus='PUBLISHED' ORDER BY planVersion DESC";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, majorCode); s.setInt(2, cohortYear);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(plan(r)) : Optional.empty(); }
        } catch (SQLException e) { throw CourseJdbc.failure("find curriculum plan", e); }
    }

    @Override
    public List<CurriculumCourse> findScheduledCourses(Connection c, String planId,
                                                       int academicYearNo, AcademicSeason season) {
        return findCourses(c, "cc.planId=? AND cc.academicYearNo=? AND cc.season=?",
                s -> { s.setString(1, planId); s.setInt(2, academicYearNo); s.setString(3, season.name()); });
    }

    @Override
    public List<CurriculumCourse> findEarlierCourses(Connection c, String planId,
                                                     int academicYearNo, AcademicSeason season) {
        return findCourses(c, "cc.planId=? AND (cc.academicYearNo<? OR (cc.academicYearNo=? AND cc.seasonOrder<?))",
                s -> { s.setString(1, planId); s.setInt(2, academicYearNo); s.setInt(3, academicYearNo);
                    s.setInt(4, season.curriculumTermOrdinal()); });
    }

    @Override
    public Set<String> findPrerequisiteCourseIds(Connection c, String planId, String courseId) {
        Set<String> ids = new LinkedHashSet<>();
        String sql = "SELECT prerequisiteCourseId FROM tblCurriculumPrerequisite WHERE planId=? AND courseId=? ORDER BY prerequisiteCourseId";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, planId); s.setString(2, courseId);
            try (ResultSet r = s.executeQuery()) { while (r.next()) ids.add(r.getString(1)); }
            return Set.copyOf(ids);
        } catch (SQLException e) { throw CourseJdbc.failure("find curriculum prerequisites", e); }
    }

    private List<CurriculumCourse> findCourses(Connection c, String predicate, Binder binder) {
        List<CurriculumCourse> values = new ArrayList<>();
        String seasonOrder = "SWITCH(cc.season='SUMMER',1,cc.season='AUTUMN',2,cc.season='SPRING',3)";
        String effectivePredicate = predicate.replace("cc.seasonOrder", seasonOrder);
        String sql = "SELECT cc.* FROM tblCurriculumCourse cc INNER JOIN tblCourse c ON cc.courseId=c.courseId WHERE "
                + effectivePredicate + " ORDER BY cc.academicYearNo," + seasonOrder + ",c.courseCode";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            binder.bind(s); try (ResultSet r = s.executeQuery()) { while (r.next()) values.add(course(r)); }
            return List.copyOf(values);
        } catch (SQLException e) { throw CourseJdbc.failure("find curriculum courses", e); }
    }

    private static CurriculumPlan plan(ResultSet r) throws SQLException {
        return new CurriculumPlan(r.getString("planId"), r.getString("majorCode"),
                r.getInt("cohortYear"), r.getString("planName"), r.getInt("planVersion"),
                r.getString("planStatus"));
    }

    private static CurriculumCourse course(ResultSet r) throws SQLException {
        return new CurriculumCourse(r.getString("planCourseId"), r.getString("planId"),
                r.getString("courseId"), r.getInt("academicYearNo"),
                AcademicSeason.valueOf(r.getString("season")), r.getString("courseNature"),
                r.getString("courseCategory"), r.getString("offeringUnit"));
    }

    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
}
