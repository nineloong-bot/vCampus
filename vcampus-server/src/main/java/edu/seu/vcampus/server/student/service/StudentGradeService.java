package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.*;

import java.util.List;

/** Student grade management operations. */
public interface StudentGradeService {
    StudentGradeView recordGrade(RecordStudentGradeCommand command, String operatorUserId);
    default StudentGradeView recordGrade(RecordStudentGradeCommand command, String operatorUserId,
            String departmentId) { return recordGrade(command, operatorUserId); }
    List<StudentGradeView> batchRecordGrades(BatchRecordGradesCommand command, String operatorUserId);
    default List<StudentGradeView> batchRecordGrades(BatchRecordGradesCommand command,
            String operatorUserId, String departmentId) {
        return batchRecordGrades(command, operatorUserId);
    }
    StudentTranscriptView getTranscriptByStudentId(String studentId);
    default StudentTranscriptView getTranscriptByStudentId(String studentId, String departmentId) {
        return getTranscriptByStudentId(studentId);
    }
    StudentTranscriptView getMyTranscript(String userId);
}
