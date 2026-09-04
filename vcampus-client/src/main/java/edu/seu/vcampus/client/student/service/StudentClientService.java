package edu.seu.vcampus.client.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Typed asynchronous facade for all ten student commands. */
public final class StudentClientService {
    private final StudentRequestClient client;
    private final Duration timeout;
    public StudentClientService(StudentRequestClient client, Duration timeout) {
        this.client = Objects.requireNonNull(client); this.timeout = Objects.requireNonNull(timeout);
    }
    private <T extends java.io.Serializable> CompletableFuture<ResponseBody<T>> sendAsync(
            String command, java.io.Serializable body) {
        return CompletableFuture.supplyAsync(() -> client.<T>send(command, body, timeout))
                .thenCompose(java.util.function.Function.identity());
    }
    public CompletableFuture<ResponseBody<StudentAdmissionResult>> admit(CreateStudentAdmissionCommand value) { return sendAsync("STUDENT_CREATE", value); }
    public CompletableFuture<ResponseBody<StudentAdmissionResult>> createManual(CreateStudentManualCommand value) { return sendAsync("STUDENT_CREATE_MANUAL", value); }
    public CompletableFuture<ResponseBody<StudentView>> getCurrent() { return sendAsync("STUDENT_GET_CURRENT", EmptyRequest.INSTANCE); }
    public CompletableFuture<ResponseBody<StudentView>> get(String id) { return sendAsync("STUDENT_GET", new EntityIdRequest(id)); }
    public CompletableFuture<ResponseBody<PageResult<StudentSummary>>> search(StudentSearchQuery value) { return sendAsync("STUDENT_SEARCH", value); }
    public CompletableFuture<ResponseBody<StudentView>> updateContact(UpdateStudentContactCommand value) { return sendAsync("STUDENT_UPDATE_CONTACT", value); }
    public CompletableFuture<ResponseBody<StudentView>> updateEnrollment(UpdateStudentEnrollmentCommand value) { return sendAsync("STUDENT_UPDATE_ENROLLMENT", value); }
    public CompletableFuture<ResponseBody<StudentView>> changeStatus(ChangeStudentStatusCommand value) { return sendAsync("STUDENT_CHANGE_STATUS", value); }
    public CompletableFuture<ResponseBody<StudentView>> updateStudentInfo(UpdateStudentInfoCommand value) { return sendAsync("STUDENT_UPDATE_INFO", value); }
    public CompletableFuture<ResponseBody<StudentView>> updateStudentAcademic(UpdateStudentAcademicCommand value) { return sendAsync("STUDENT_UPDATE_ACADEMIC", value); }
    public CompletableFuture<ResponseBody<ArrayList<DepartmentView>>> listDepartments(boolean activeOnly) { return sendAsync("STUDENT_LIST_DEPARTMENTS", new ActiveOnlyQuery(activeOnly)); }
    public CompletableFuture<ResponseBody<ArrayList<MajorView>>> listMajors(String departmentId) { return listMajors(departmentId, true); }
    public CompletableFuture<ResponseBody<ArrayList<MajorView>>> listMajors(String departmentId, boolean activeOnly) { return sendAsync("STUDENT_LIST_MAJORS", new OrganizationChildrenQuery(departmentId, activeOnly)); }
    public CompletableFuture<ResponseBody<ArrayList<ClassView>>> listClasses(String majorId) { return listClasses(majorId, true); }
    public CompletableFuture<ResponseBody<ArrayList<ClassView>>> listClasses(String majorId, boolean activeOnly) { return sendAsync("STUDENT_LIST_CLASSES", new OrganizationChildrenQuery(majorId, activeOnly)); }
    public CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>> listChanges(String studentId) { return sendAsync("STUDENT_GET_CHANGES", new EntityIdRequest(studentId)); }
    public CompletableFuture<ResponseBody<DepartmentView>> saveDepartment(SaveDepartmentCommand value) { return sendAsync("STUDENT_SAVE_DEPARTMENT", value); }
    public CompletableFuture<ResponseBody<MajorView>> saveMajor(SaveMajorCommand value) { return sendAsync("STUDENT_SAVE_MAJOR", value); }
    public CompletableFuture<ResponseBody<ClassView>> saveClass(SaveClassCommand value) { return sendAsync("STUDENT_SAVE_CLASS", value); }
    public CompletableFuture<ResponseBody<StudentProfileWorkspace>> getProfileWorkspace() { return sendAsync("STUDENT_PROFILE_GET_WORKSPACE", EmptyRequest.INSTANCE); }
    public CompletableFuture<ResponseBody<StudentProfileData>> getProfile(String studentId) { return sendAsync("STUDENT_GET_PROFILE", new EntityIdRequest(studentId)); }
    public CompletableFuture<ResponseBody<StudentProfileWorkspace>> savePersonalDraft(SaveStudentPersonalDraftCommand value) { return sendAsync("STUDENT_PROFILE_SAVE_PERSONAL_DRAFT", value); }
    public CompletableFuture<ResponseBody<StudentProfileWorkspace>> saveAttendanceDraft(SaveStudentAttendanceDraftCommand value) { return sendAsync("STUDENT_PROFILE_SAVE_ATTENDANCE_DRAFT", value); }
    public CompletableFuture<ResponseBody<StudentProfileWorkspace>> submitProfile(SubmitStudentProfileCommand value) { return sendAsync("STUDENT_PROFILE_SUBMIT", value); }
    public CompletableFuture<ResponseBody<StudentProfileWorkspace>> withdrawProfile(WithdrawStudentProfileCommand value) { return sendAsync("STUDENT_PROFILE_WITHDRAW", value); }
    public CompletableFuture<ResponseBody<PdfDocument>> exportProfilePdf() { return sendAsync("STUDENT_PROFILE_EXPORT_PDF", EmptyRequest.INSTANCE); }
    public CompletableFuture<ResponseBody<PageResult<StudentProfileApplicationView>>> listProfileReviews(StudentProfileReviewQuery value) { return sendAsync("STUDENT_PROFILE_REVIEW_LIST", value); }
    public CompletableFuture<ResponseBody<StudentProfileWorkspace>> getProfileReview(String applicationId) { return sendAsync("STUDENT_PROFILE_REVIEW_GET", new EntityIdRequest(applicationId)); }
    public CompletableFuture<ResponseBody<StudentProfileApplicationView>> approveProfile(ReviewStudentProfileCommand value) { return sendAsync("STUDENT_PROFILE_APPROVE", value); }
    public CompletableFuture<ResponseBody<StudentProfileApplicationView>> rejectProfile(ReviewStudentProfileCommand value) { return sendAsync("STUDENT_PROFILE_REJECT", value); }
}
