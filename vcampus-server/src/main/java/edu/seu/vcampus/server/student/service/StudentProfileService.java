package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.*;

/** Student-owned drafts and administrator review operations. */
public interface StudentProfileService {
    /**
     * Performs the get workspace operation.
     * @param userId the user identifier
     * @return the operation result
     */
    StudentProfileWorkspace getWorkspace(String userId);
    /**
     * Performs the get profile by student identifier operation.
     * @param studentId the student identifier
     * @return the operation result
     */
    StudentProfileData getProfileByStudentId(String studentId);
    /**
     * Performs the get profile by student identifier operation.
     * @param studentId the student identifier
     * @param departmentId the department identifier
     * @return the operation result
     */
    default StudentProfileData getProfileByStudentId(String studentId, String departmentId) {
        return getProfileByStudentId(studentId);
    }
    /**
     * Performs the save personal draft operation.
     * @param userId the user identifier
     * @param command the command
     * @return the operation result
     */
    StudentProfileWorkspace savePersonalDraft(String userId, SaveStudentPersonalDraftCommand command);
    /**
     * Performs the save attendance draft operation.
     * @param userId the user identifier
     * @param command the command
     * @return the operation result
     */
    StudentProfileWorkspace saveAttendanceDraft(String userId, SaveStudentAttendanceDraftCommand command);
    /**
     * Performs the submit operation.
     * @param userId the user identifier
     * @param command the command
     * @return the operation result
     */
    StudentProfileWorkspace submit(String userId, SubmitStudentProfileCommand command);
    /**
     * Performs the withdraw operation.
     * @param userId the user identifier
     * @param command the command
     * @return the operation result
     */
    StudentProfileWorkspace withdraw(String userId, WithdrawStudentProfileCommand command);
    /**
     * Performs the list pending operation.
     * @param query the query
     * @return the operation result
     */
    PageResult<StudentProfileApplicationView> listPending(StudentProfileReviewQuery query);
    default PageResult<StudentProfileApplicationView> listPending(StudentProfileReviewQuery query,
            String departmentId) { return listPending(query); }
    /**
     * Performs the get application operation.
     * @param applicationId the application identifier
     * @return the operation result
     */
    StudentProfileWorkspace getApplication(String applicationId);
    /**
     * Performs the get application operation.
     * @param applicationId the application identifier
     * @param departmentId the department identifier
     * @return the operation result
     */
    default StudentProfileWorkspace getApplication(String applicationId, String departmentId) {
        return getApplication(applicationId);
    }
    /**
     * Performs the approve operation.
     * @param applicationId the application identifier
     * @param reviewerUserId the reviewer user identifier
     * @param reviewComment the review comment
     * @return the operation result
     */
    StudentProfileApplicationView approve(String applicationId, String reviewerUserId,
                                          String reviewComment);
    /**
     * Performs the approve operation.
     * @param applicationId the application identifier
     * @param reviewerUserId the reviewer user identifier
     * @param reviewComment the review comment
     * @param departmentId the department identifier
     * @return the operation result
     */
    default StudentProfileApplicationView approve(String applicationId, String reviewerUserId,
            String reviewComment, String departmentId) {
        return approve(applicationId, reviewerUserId, reviewComment);
    }
    /**
     * Performs the reject operation.
     * @param applicationId the application identifier
     * @param reviewerUserId the reviewer user identifier
     * @param reviewComment the review comment
     * @return the operation result
     */
    StudentProfileApplicationView reject(String applicationId, String reviewerUserId,
                                         String reviewComment);
    /**
     * Performs the reject operation.
     * @param applicationId the application identifier
     * @param reviewerUserId the reviewer user identifier
     * @param reviewComment the review comment
     * @param departmentId the department identifier
     * @return the operation result
     */
    default StudentProfileApplicationView reject(String applicationId, String reviewerUserId,
            String reviewComment, String departmentId) {
        return reject(applicationId, reviewerUserId, reviewComment);
    }
}
