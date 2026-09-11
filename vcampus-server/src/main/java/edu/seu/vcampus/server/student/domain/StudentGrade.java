package edu.seu.vcampus.server.student.domain;

import edu.seu.vcampus.common.student.GradeResult;

import java.time.Instant;

/** Persistence model for a student grade record. */
public record StudentGrade(
        String gradeId,
        String studentId,
        String planCourseId,
        GradeResult result,
        String recordedSemester,
        String operatorUserId,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) { }
