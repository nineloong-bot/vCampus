package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Command to batch-import courses into a training plan. */
/**
 * Carries immutable import training plan courses command data.
 * @param planId the plan identifier
 * @param courses the courses
 */
public record ImportTrainingPlanCoursesCommand(
        String planId,
        List<CourseEntry> courses) implements Serializable {

    /**
 * Carries immutable course entry data.
 * @param courseCode the course code
 * @param courseName the course name
 * @param credits the credits
 * @param courseType the course type
 * @param semester the semester
 */
public record CourseEntry(
            String courseCode,
            String courseName,
            BigDecimal credits,
            CourseType courseType,
            int semester) implements Serializable { }
}
