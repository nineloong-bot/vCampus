package edu.seu.vcampus.client.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.common.student.majortransfer.*;
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
    public CompletableFuture<ResponseBody<BatchImportResult>> batchImport(BatchImportCommand value) { return sendAsync("STUDENT_BATCH_IMPORT", value); }
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

    // ── Major Transfer: Student ──
    public CompletableFuture<ResponseBody<MajorTransferWorkspace>> getTransferWorkspace() { return sendAsync("MAJOR_TRANSFER_GET_WORKSPACE", EmptyRequest.INSTANCE); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> saveTransferDraft(SaveMajorTransferDraftCommand value) { return sendAsync("MAJOR_TRANSFER_SAVE_DRAFT", value); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> uploadTransferAttachment(UploadMajorTransferAttachmentCommand value) { return sendAsync("MAJOR_TRANSFER_UPLOAD_ATTACHMENT", value); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> deleteTransferAttachment(DeleteMajorTransferAttachmentCommand value) { return sendAsync("MAJOR_TRANSFER_DELETE_ATTACHMENT", value); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> submitTransfer(SubmitMajorTransferCommand value) { return sendAsync("MAJOR_TRANSFER_SUBMIT", value); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> withdrawTransfer(WithdrawMajorTransferCommand value) { return sendAsync("MAJOR_TRANSFER_WITHDRAW", value); }

    // ── Major Transfer: Admin ──
    public CompletableFuture<ResponseBody<MajorTransferAttachmentDocument>> getTransferAttachment(String id) { return sendAsync("MAJOR_TRANSFER_GET_ATTACHMENT", new EntityIdRequest(id)); }
    public CompletableFuture<ResponseBody<MajorTransferBatchView>> saveTransferBatch(SaveMajorTransferBatchCommand value) { return sendAsync("MAJOR_TRANSFER_SAVE_BATCH", value); }
    public CompletableFuture<ResponseBody<MajorTransferOptionView>> saveTransferOption(SaveMajorTransferOptionCommand value) { return sendAsync("MAJOR_TRANSFER_SAVE_OPTION", value); }
    public CompletableFuture<ResponseBody<ArrayList<MajorTransferBatchView>>> listTransferBatches() { return sendAsync("MAJOR_TRANSFER_LIST_BATCHES", EmptyRequest.INSTANCE); }
    public CompletableFuture<ResponseBody<ArrayList<MajorTransferOptionView>>> listTransferOptions(String batchId) { return sendAsync("MAJOR_TRANSFER_LIST_OPTIONS", new EntityIdRequest(batchId)); }
    public CompletableFuture<ResponseBody<ArrayList<MajorTransferApplicationView>>> listTransferApplications(MajorTransferApplicationQuery value) { return sendAsync("MAJOR_TRANSFER_LIST_APPLICATIONS", value); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> getTransferApplication(String applicationId) { return sendAsync("MAJOR_TRANSFER_GET_APPLICATION", new EntityIdRequest(applicationId)); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> reviewTransferSource(ReviewMajorTransferSourceCommand value) { return sendAsync("MAJOR_TRANSFER_REVIEW_SOURCE", value); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> reviewTransferQualification(ReviewMajorTransferQualificationCommand value) { return sendAsync("MAJOR_TRANSFER_REVIEW_QUALIFICATION", value); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> recordTransferScore(RecordMajorTransferScoreCommand value) { return sendAsync("MAJOR_TRANSFER_RECORD_SCORE", value); }
    public CompletableFuture<ResponseBody<MajorTransferRankingView>> generateTransferProposal(GenerateMajorTransferProposalCommand value) { return sendAsync("MAJOR_TRANSFER_GENERATE_PROPOSAL", value); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> finalizeTransfer(FinalizeMajorTransferCommand value) { return sendAsync("MAJOR_TRANSFER_FINALIZE", value); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> executeTransfer(ExecuteMajorTransferCommand value) { return sendAsync("MAJOR_TRANSFER_EXECUTE", value); }
    public CompletableFuture<ResponseBody<MajorTransferApplicationView>> cancelTransfer(CancelMajorTransferCommand value) { return sendAsync("MAJOR_TRANSFER_CANCEL", value); }

    // ── Training Plan: Admin ──
    public CompletableFuture<ResponseBody<TrainingPlanDetailView>> saveTrainingPlan(SaveTrainingPlanCommand value) { return sendAsync("TRAINING_PLAN_SAVE", value); }
    public CompletableFuture<ResponseBody<TrainingPlanDetailView>> getTrainingPlan(String planId) { return sendAsync("TRAINING_PLAN_GET", new EntityIdRequest(planId)); }
    public CompletableFuture<ResponseBody<PageResult<TrainingPlanSummary>>> searchTrainingPlans(TrainingPlanQuery value) { return sendAsync("TRAINING_PLAN_LIST", value); }
    public CompletableFuture<ResponseBody<TrainingPlanCourseView>> saveTrainingPlanCourse(SaveTrainingPlanCourseCommand value) { return sendAsync("TRAINING_PLAN_SAVE_COURSE", value); }
    public CompletableFuture<ResponseBody<edu.seu.vcampus.common.protocol.EmptyResponse>> removeTrainingPlanCourse(String planCourseId) { return sendAsync("TRAINING_PLAN_REMOVE_COURSE", new EntityIdRequest(planCourseId)); }
    public CompletableFuture<ResponseBody<ArrayList<TrainingPlanCourseView>>> importTrainingPlanCourses(ImportTrainingPlanCoursesCommand value) { return sendAsync("TRAINING_PLAN_IMPORT_COURSES", value); }

    // ── Training Plan: Student ──
    public CompletableFuture<ResponseBody<TrainingPlanDetailView>> getMyTrainingPlan() { return sendAsync("TRAINING_PLAN_GET_MY", EmptyRequest.INSTANCE); }

    // ── Grades: Admin ──
    public CompletableFuture<ResponseBody<StudentGradeView>> recordGrade(RecordStudentGradeCommand value) { return sendAsync("GRADE_RECORD", value); }
    public CompletableFuture<ResponseBody<ArrayList<StudentGradeView>>> batchRecordGrades(BatchRecordGradesCommand value) { return sendAsync("GRADE_BATCH_RECORD", value); }
    public CompletableFuture<ResponseBody<StudentTranscriptView>> getStudentTranscript(String studentId) { return sendAsync("GRADE_LIST_BY_STUDENT", new EntityIdRequest(studentId)); }

    // ── Grades: Student ──
    public CompletableFuture<ResponseBody<StudentTranscriptView>> getMyTranscript() { return sendAsync("GRADE_GET_MY", EmptyRequest.INSTANCE); }
}
