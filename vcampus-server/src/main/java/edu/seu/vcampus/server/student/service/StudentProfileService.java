package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.*;

/** Student-owned drafts and administrator review operations. */
public interface StudentProfileService {
    StudentProfileWorkspace getWorkspace(String userId);
    StudentProfileWorkspace savePersonalDraft(String userId, SaveStudentPersonalDraftCommand command);
    StudentProfileWorkspace saveAttendanceDraft(String userId, SaveStudentAttendanceDraftCommand command);
    StudentProfileWorkspace submit(String userId, SubmitStudentProfileCommand command);
    PageResult<StudentProfileApplicationView> listPending(StudentProfileReviewQuery query);
    StudentProfileWorkspace getApplication(String applicationId);
    StudentProfileApplicationView approve(String applicationId, String reviewerUserId,
                                          String reviewComment);
    StudentProfileApplicationView reject(String applicationId, String reviewerUserId,
                                         String reviewComment);
}
