package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Command to record or update a single grade for a student. */
public record RecordStudentGradeCommand(
        String studentId,
        String planCourseId,
        GradeResult result,
        String recordedSemester) implements Serializable { }
