package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.*;

import java.util.List;

/** Student grade management operations. */
public interface StudentGradeService {
    /**
     * Performs the record grade operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    StudentGradeView recordGrade(RecordStudentGradeCommand command, String operatorUserId);
    default StudentGradeView recordGrade(RecordStudentGradeCommand command, String operatorUserId,
            String departmentId) { return recordGrade(command, operatorUserId); }
    /**
     * Performs the batch record grades operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    List<StudentGradeView> batchRecordGrades(BatchRecordGradesCommand command, String operatorUserId);
    /**
     * Performs the batch record grades operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @param departmentId the department identifier
     * @return the operation result
     */
    default List<StudentGradeView> batchRecordGrades(BatchRecordGradesCommand command,
            String operatorUserId, String departmentId) {
        return batchRecordGrades(command, operatorUserId);
    }
    /**
     * Performs the get transcript by student identifier operation.
     * @param studentId the student identifier
     * @return the operation result
     */
    StudentTranscriptView getTranscriptByStudentId(String studentId);
    /**
     * Performs the get transcript by student identifier operation.
     * @param studentId the student identifier
     * @param departmentId the department identifier
     * @return the operation result
     */
    default StudentTranscriptView getTranscriptByStudentId(String studentId, String departmentId) {
        return getTranscriptByStudentId(studentId);
    }
    /**
     * Performs the get my transcript operation.
     * @param userId the user identifier
     * @return the operation result
     */
    StudentTranscriptView getMyTranscript(String userId);
}
