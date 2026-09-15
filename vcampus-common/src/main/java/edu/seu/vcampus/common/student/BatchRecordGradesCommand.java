package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.List;

/** Command to batch-record grades for multiple students. */
/**
 * Carries immutable batch record grades command data.
 * @param entries the entries
 */
public record BatchRecordGradesCommand(
        List<GradeEntry> entries) implements Serializable {

    /**
 * Carries immutable grade entry data.
 * @param studentId the student identifier
 * @param planCourseId the plan course identifier
 * @param result the result
 * @param recordedSemester the recorded semester
 */
public record GradeEntry(
            String studentId,
            String planCourseId,
            GradeResult result,
            String recordedSemester) implements Serializable { }
}
