package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.RetakeCommand;
import edu.seu.vcampus.common.course.RetakeEligibility;
import edu.seu.vcampus.common.course.AdjustmentAuditQuery;
import edu.seu.vcampus.common.course.AdjustmentAuditView;
import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.course.CourseView;
import edu.seu.vcampus.common.course.TermView;
import edu.seu.vcampus.common.course.ImportCourseOutcomesCommand;
import edu.seu.vcampus.common.course.CreateCourseCommand;
import edu.seu.vcampus.common.course.UpdateCourseCommand;
import edu.seu.vcampus.common.course.CreateTermCommand;
import edu.seu.vcampus.common.course.UpdateTermCommand;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.UpdateOfferingCommand;
import edu.seu.vcampus.common.course.OfferingView;
import edu.seu.vcampus.common.course.TermPhaseView;
import edu.seu.vcampus.common.course.SelectionPhaseView;
import edu.seu.vcampus.common.course.CreateSelectionPhaseCommand;
import edu.seu.vcampus.common.course.UpdateSelectionPhaseCommand;
import edu.seu.vcampus.common.course.ChangeSelectionPhaseStatusCommand;
import edu.seu.vcampus.common.course.StudentSelectionContextView;
import edu.seu.vcampus.common.course.CourseSelectionQuery;
import edu.seu.vcampus.common.course.CourseSelectionView;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Production adapter from Swing's narrow seam to the typed course client. */
public final class CourseClientGateway implements CourseUiGateway {
    private final CourseClientService client;
    private final UserClientService users;

    public CourseClientGateway(CourseClientService client) {
        this.client = Objects.requireNonNull(client);
        this.users = null;
    }

    public CourseClientGateway(CourseClientService client, UserClientService users) {
        this.client = Objects.requireNonNull(client);
        this.users = Objects.requireNonNull(users);
    }

    public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
        return client.searchOfferings(query);
    }

    public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
        return client.getCurrentEnrollments();
    }

    public CompletableFuture<List<ScheduleItem>> currentSchedule() {
        return client.getCurrentSchedule();
    }

    public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) {
        return client.enroll(command);
    }

    public CompletableFuture<EnrollmentView> lateAdd(LateAddCommand command) { return client.addDuringAdjustment(command); }
    public CompletableFuture<EmptyResponse> drop(DropCommand command) { return client.drop(command); }
    public CompletableFuture<EnrollmentView> change(ChangeOfferingCommand command) { return client.changeDuringAdjustment(command); }
    public CompletableFuture<RetakeEligibility> checkRetake(String courseId) { return client.checkRetakeEligibility(courseId); }
    public CompletableFuture<EnrollmentView> enrollRetake(RetakeCommand command) { return client.enrollRetake(command); }
    public CompletableFuture<PageResult<AdjustmentAuditView>> searchAdjustmentAudits(AdjustmentAuditQuery query) { return client.searchAdjustmentAudits(query); }
    public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) { return client.searchCatalog(query); }
    public CompletableFuture<PageResult<UserSummary>> searchTeachers(String keyword) {
        if (users == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("User client is not connected"));
        }
        return users.searchUsers(new UserSearchQuery(
                keyword, UserRole.TEACHER, AccountStatus.ACTIVE, 0, 100));
    }
    public CompletableFuture<Optional<UserSummary>> resolveTeacher(String userId) {
        Objects.requireNonNull(userId, "userId");
        if (users == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("User client is not connected"));
        }
        return resolveTeacher(userId, 0);
    }

    private CompletableFuture<Optional<UserSummary>> resolveTeacher(String userId, int pageNumber) {
        return users.searchUsers(new UserSearchQuery(
                        null, UserRole.TEACHER, AccountStatus.ACTIVE, pageNumber, 100))
                .thenCompose(page -> {
                    Optional<UserSummary> match = page.items().stream()
                            .filter(teacher -> userId.equals(teacher.userId())).findFirst();
                    long consumed = ((long) pageNumber + 1) * 100;
                    if (match.isPresent() || page.items().isEmpty() || consumed >= page.total()) {
                        return CompletableFuture.completedFuture(match);
                    }
                    return resolveTeacher(userId, pageNumber + 1);
                });
    }
    public CompletableFuture<List<TermView>> listTerms() { return client.listTerms(); }
    public CompletableFuture<EmptyResponse> importOutcomes(ImportCourseOutcomesCommand command) { return client.importOutcomes(command); }
    public CompletableFuture<CourseView> createCourse(CreateCourseCommand command) { return client.createCourse(command); }
    public CompletableFuture<CourseView> updateCourse(UpdateCourseCommand command) { return client.updateCourse(command); }
    public CompletableFuture<TermView> createTerm(CreateTermCommand command) { return client.createTerm(command); }
    public CompletableFuture<TermView> updateTerm(UpdateTermCommand command) { return client.updateTerm(command); }
    public CompletableFuture<OfferingView> createOffering(CreateOfferingCommand command) { return client.createOffering(command); }
    public CompletableFuture<OfferingView> updateOffering(UpdateOfferingCommand command) { return client.updateOffering(command); }
    /** Compatibility projection for the legacy "my enrollments" view, backed by the manual phase. */
    public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
        return client.getStudentSelectionContext().thenApply(context -> {
            var now = context.serverTime();
            var phase = "OPEN".equals(context.phaseStatus()) ? context.phaseType() : "READ_ONLY";
            return new TermPhaseView(context.termId(), context.termStatus(), phase, now,
                    now, now.plusNanos(1), now.plusNanos(2), now.plusNanos(3));
        });
    }
    public CompletableFuture<List<SelectionPhaseView>> listSelectionPhases() { return client.listSelectionPhases(); }
    public CompletableFuture<SelectionPhaseView> createSelectionPhase(CreateSelectionPhaseCommand command) { return client.createSelectionPhase(command); }
    public CompletableFuture<SelectionPhaseView> updateSelectionPhase(UpdateSelectionPhaseCommand command) { return client.updateSelectionPhase(command); }
    public CompletableFuture<SelectionPhaseView> changeSelectionPhaseStatus(ChangeSelectionPhaseStatusCommand command) { return client.changeSelectionPhaseStatus(command); }
    public CompletableFuture<StudentSelectionContextView> studentSelectionContext() { return client.getStudentSelectionContext(); }
    public CompletableFuture<PageResult<CourseSelectionView>> searchStudentCourses(CourseSelectionQuery query) { return client.searchStudentCourses(query); }
    public CompletableFuture<String> currentTermId() { return client.getCurrentTerm().thenApply(TermView::termId); }
}
