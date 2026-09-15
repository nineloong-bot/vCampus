package edu.seu.vcampus.common.course;

import java.io.Serializable;
import java.math.BigDecimal;

/** Immutable course definition supplied by an approved training plan. */
public record CurriculumCourseCandidate(
        String planCourseId,
        String courseCode,
        String courseName,
        BigDecimal credits,
        int totalHours,
        String courseNature,
        String departmentId,
        String departmentName,
        boolean conflicted,
        String conflictMessage) implements Serializable { }
