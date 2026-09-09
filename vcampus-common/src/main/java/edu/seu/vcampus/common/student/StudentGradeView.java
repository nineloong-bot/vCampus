package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** A single grade record for display. */
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
