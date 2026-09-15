package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Command to record or update a single grade for a student. */
/**
 * Carries immutable record student grade command data.
 * @param studentId the student identifier
 * @param planCourseId the plan course identifier
 * @param result the result
 * @param recordedSemester the recorded semester
 */
public record RecordStudentGradeCommand(
        String studentId,
        String planCourseId,
        GradeResult result,
        String recordedSemester) implements Serializable { }
