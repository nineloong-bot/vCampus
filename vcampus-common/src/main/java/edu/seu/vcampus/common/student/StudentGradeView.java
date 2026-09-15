package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** A single grade record for display. */
/**
 * Carries immutable student grade view data.
 * @param gradeId the grade identifier
 * @param studentId the student identifier
 * @param planCourseId the plan course identifier
 * @param courseCode the course code
 * @param courseName the course name
 * @param credits the credits
 * @param courseType the course type
 * @param semester the semester
 * @param result the result
 * @param recordedSemester the recorded semester
 * @param rowVersion the row version
 */
public record StudentGradeView(
        String gradeId,
        String studentId,
        String planCourseId,
        String courseCode,
        String courseName,
        BigDecimal credits,
        CourseType courseType,
        int semester,
        GradeResult result,
        String recordedSemester,
        long rowVersion) implements Serializable { }
