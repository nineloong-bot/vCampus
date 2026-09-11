package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.*;

import java.util.List;

/** Student grade management operations. */
public interface StudentGradeService {
    StudentGradeView recordGrade(RecordStudentGradeCommand command, String operatorUserId);
    List<StudentGradeView> batchRecordGrades(BatchRecordGradesCommand command, String operatorUserId);
    StudentTranscriptView getTranscriptByStudentId(String studentId);
    StudentTranscriptView getMyTranscript(String userId);
}
