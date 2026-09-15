package edu.seu.vcampus.server.course.repository;

import edu.seu.vcampus.common.course.AcademicSeason;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Replaceable read-model boundary for curriculum-aware course selection. */
public interface CurriculumRepository {
    /**
     * Performs the insert plan operation.
     * @param connection the connection
     * @param plan the plan
     * @return the operation result
     */
    CurriculumPlan insertPlan(Connection connection, CurriculumPlan plan);
    /**
     * Performs the insert course operation.
     * @param connection the connection
     * @param course the course
     * @return the operation result
     */
    CurriculumCourse insertCourse(Connection connection, CurriculumCourse course);
    /**
     * Performs the insert prerequisite operation.
     * @param connection the connection
     * @param id the id
     * @param planId the plan identifier
     * @param courseId the course identifier
     * @param prerequisiteCourseId the prerequisite course identifier
     */
    void insertPrerequisite(Connection connection, String id, String planId,
                            String courseId, String prerequisiteCourseId);
    /**
     * Performs the find published plan operation.
     * @param connection the connection
     * @param majorCode the major code
     * @param cohortYear the cohort year
     * @return the operation result
     */
    Optional<CurriculumPlan> findPublishedPlan(Connection connection, String majorCode, int cohortYear);
    /**
     * Performs the find scheduled courses operation.
     * @param connection the connection
     * @param planId the plan identifier
     * @param academicYearNo the academic year no
     * @param season the season
     * @return the operation result
     */
    List<CurriculumCourse> findScheduledCourses(Connection connection, String planId,
                                                int academicYearNo, AcademicSeason season);
    /**
     * Performs the find earlier courses operation.
     * @param connection the connection
     * @param planId the plan identifier
     * @param academicYearNo the academic year no
     * @param season the season
     * @return the operation result
     */
    List<CurriculumCourse> findEarlierCourses(Connection connection, String planId,
                                              int academicYearNo, AcademicSeason season);
    /**
     * Performs the find prerequisite course identifiers operation.
     * @param connection the connection
     * @param planId the plan identifier
     * @param courseId the course identifier
     * @return the operation result
     */
    Set<String> findPrerequisiteCourseIds(Connection connection, String planId, String courseId);
}
