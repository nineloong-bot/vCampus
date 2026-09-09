package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.List;

/** Command to batch-record grades for multiple students. */
public record BatchRecordGradesCommand(
        List<GradeEntry> entries) implements Serializable {

    public record GradeEntry(
            String studentId,
            String planCourseId,
            GradeResult result,
            String recordedSemester) implements Serializable { }
}
