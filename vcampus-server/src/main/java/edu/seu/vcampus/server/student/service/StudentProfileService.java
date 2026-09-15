package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.*;

/** Student-owned drafts and administrator review operations. */
public interface StudentProfileService {
    StudentProfileWorkspace getWorkspace(String userId);
    StudentProfileData getProfileByStudentId(String studentId);
    default StudentProfileData getProfileByStudentId(String studentId, String departmentId) {
        return getProfileByStudentId(studentId);
    }
    StudentProfileWorkspace savePersonalDraft(String userId, SaveStudentPersonalDraftCommand command);
    StudentProfileWorkspace saveAttendanceDraft(String userId, SaveStudentAttendanceDraftCommand command);
    StudentProfileWorkspace submit(String userId, SubmitStudentProfileCommand command);
    StudentProfileWorkspace withdraw(String userId, WithdrawStudentProfileCommand command);
    PageResult<StudentProfileApplicationView> listPending(StudentProfileReviewQuery query);
    default PageResult<StudentProfileApplicationView> listPending(StudentProfileReviewQuery query,
            String departmentId) { return listPending(query); }
    StudentProfileWorkspace getApplication(String applicationId);
    default StudentProfileWorkspace getApplication(String applicationId, String departmentId) {
        return getApplication(applicationId);
    }
    StudentProfileApplicationView approve(String applicationId, String reviewerUserId,
                                          String reviewComment);
    default StudentProfileApplicationView approve(String applicationId, String reviewerUserId,
            String reviewComment, String departmentId) {
        return approve(applicationId, reviewerUserId, reviewComment);
    }
    StudentProfileApplicationView reject(String applicationId, String reviewerUserId,
                                         String reviewComment);
    default StudentProfileApplicationView reject(String applicationId, String reviewerUserId,
            String reviewComment, String departmentId) {
        return reject(applicationId, reviewerUserId, reviewComment);
    }
}
