package edu.seu.vcampus.server.course.repository;

import edu.seu.vcampus.common.course.AcademicSeason;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Replaceable read-model boundary for curriculum-aware course selection. */
public interface CurriculumRepository {
    CurriculumPlan insertPlan(Connection connection, CurriculumPlan plan);
    CurriculumCourse insertCourse(Connection connection, CurriculumCourse course);
    void insertPrerequisite(Connection connection, String id, String planId,
                            String courseId, String prerequisiteCourseId);
    Optional<CurriculumPlan> findPublishedPlan(Connection connection, String majorCode, int cohortYear);
    List<CurriculumCourse> findScheduledCourses(Connection connection, String planId,
                                                int academicYearNo, AcademicSeason season);
    List<CurriculumCourse> findEarlierCourses(Connection connection, String planId,
                                              int academicYearNo, AcademicSeason season);
    Set<String> findPrerequisiteCourseIds(Connection connection, String planId, String courseId);
}
