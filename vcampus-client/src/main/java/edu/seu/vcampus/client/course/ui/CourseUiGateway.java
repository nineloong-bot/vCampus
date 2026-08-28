package edu.seu.vcampus.client.course.ui;

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
import edu.seu.vcampus.common.course.TermPhaseView;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.paging.PageResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Narrow asynchronous seam used by course Swing pages. */
public interface CourseUiGateway {
    CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query);
    CompletableFuture<List<EnrollmentView>> currentEnrollments();
    CompletableFuture<List<ScheduleItem>> currentSchedule();
    CompletableFuture<EnrollmentView> enroll(EnrollCommand command);
    default CompletableFuture<EnrollmentView> lateAdd(LateAddCommand command) { return unsupported(); }
    default CompletableFuture<EmptyResponse> drop(DropCommand command) { return unsupported(); }
    default CompletableFuture<EnrollmentView> change(ChangeOfferingCommand command) { return unsupported(); }
    default CompletableFuture<RetakeEligibility> checkRetake(String courseId) { return unsupported(); }
    default CompletableFuture<EnrollmentView> enrollRetake(RetakeCommand command) { return unsupported(); }
    default CompletableFuture<PageResult<AdjustmentAuditView>> searchAdjustmentAudits(AdjustmentAuditQuery query) { return unsupported(); }
    default CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) { return unsupported(); }
    default CompletableFuture<List<TermView>> listTerms() { return unsupported(); }
    default CompletableFuture<EmptyResponse> importOutcomes(ImportCourseOutcomesCommand command) { return unsupported(); }
    default CompletableFuture<CourseView> createCourse(CreateCourseCommand command) { return unsupported(); }
    default CompletableFuture<CourseView> updateCourse(UpdateCourseCommand command) { return unsupported(); }
    default CompletableFuture<TermPhaseView> getTermPhase(String termId) { return unsupported(); }

    private static <T> CompletableFuture<T> unsupported() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Course operation is not connected"));
    }

    static CourseUiGateway preview() {
        List<ScheduleItem> schedule = List.of(
                new ScheduleItem("s1", "o1", "MATH101", "高等数学", "01班", "张老师", "MONDAY", 1, 2, 1, 16, "教一-201"),
                new ScheduleItem("s2", "o2", "CS201", "数据结构", "02班", "李老师", "WEDNESDAY", 3, 4, 1, 16, "计算中心-305"),
                new ScheduleItem("s3", "o3", "PHYS101", "大学物理", "01班", "王老师", "FRIDAY", 5, 6, 1, 16, "教三-108"));
        List<OfferingSummary> offerings = schedule.stream().map(item -> new OfferingSummary(
                item.offeringId(), "2026-autumn", item.offeringId(), item.courseCode(), item.courseName(),
                item.teacherUserId(), item.className(), 40, 28, "OPEN", 0, List.of(item))).toList();
        return new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                return CompletableFuture.completedFuture(new PageResult<>(offerings, 0, 20, offerings.size()));
            }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of(new EnrollmentView(
                        "preview-enrollment", "o1", "preview-student", "NORMAL", "ACTIVE",
                        java.time.Instant.parse("2026-08-25T08:00:00Z"), null, 0)));
            }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() {
                return CompletableFuture.completedFuture(schedule);
            }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) {
                return CompletableFuture.completedFuture(new EnrollmentView(
                        "preview-enrollment", command.offeringId(), "preview-student", "NORMAL", "ACTIVE",
                        java.time.Instant.now(), null, 0));
            }
            public CompletableFuture<EnrollmentView> lateAdd(LateAddCommand command) {
                return enroll(new EnrollCommand(command.offeringId()));
            }
            public CompletableFuture<EmptyResponse> drop(DropCommand command) {
                return CompletableFuture.completedFuture(EmptyResponse.INSTANCE);
            }
            public CompletableFuture<EnrollmentView> change(ChangeOfferingCommand command) {
                return CompletableFuture.completedFuture(new EnrollmentView(
                        command.sourceEnrollmentId(), command.targetOfferingId(), "preview-student", "NORMAL", "ACTIVE",
                        java.time.Instant.now(), null, command.expectedVersion() + 1));
            }
            public CompletableFuture<RetakeEligibility> checkRetake(String courseId) {
                return CompletableFuture.completedFuture(new RetakeEligibility(courseId, true, List.of("preview-attempt"), "FAILED_ATTEMPT"));
            }
            public CompletableFuture<EnrollmentView> enrollRetake(RetakeCommand command) {
                return CompletableFuture.completedFuture(new EnrollmentView(
                        "preview-retake", command.offeringId(), "preview-student", "RETAKE", "ACTIVE",
                        java.time.Instant.now(), null, 0));
            }
            public CompletableFuture<PageResult<AdjustmentAuditView>> searchAdjustmentAudits(AdjustmentAuditQuery query) {
                return CompletableFuture.completedFuture(new PageResult<>(List.of(new AdjustmentAuditView(
                        "preview-adjustment", "20260001", "CHANGE", "o1", "o2", "SUCCEEDED", null,
                        java.time.Instant.parse("2026-08-27T06:32:00Z"))), 0, query.pageSize(), 1));
            }
            public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                List<CourseView> courses = List.of(
                        new CourseView("c1", "MATH101", "高等数学", new java.math.BigDecimal("5.0"), 80,
                                "理工科基础课程", true, 2, java.time.Instant.parse("2026-08-20T00:00:00Z"), java.time.Instant.parse("2026-08-27T00:00:00Z")),
                        new CourseView("c2", "CS201", "数据结构", new java.math.BigDecimal("4.0"), 64,
                                "计算机专业基础课程", true, 1, java.time.Instant.parse("2026-08-20T00:00:00Z"), java.time.Instant.parse("2026-08-27T00:00:00Z")));
                return CompletableFuture.completedFuture(new PageResult<>(courses, 0, query.pageSize(), courses.size()));
            }
            public CompletableFuture<List<TermView>> listTerms() {
                return CompletableFuture.completedFuture(List.of(new TermView(
                        "2026-autumn", "2026-2027-1", "2026—2027学年秋季学期",
                        java.time.LocalDate.parse("2026-09-01"), java.time.LocalDate.parse("2027-01-15"),
                        java.time.Instant.parse("2026-08-20T00:00:00Z"), java.time.Instant.parse("2026-08-31T16:00:00Z"),
                        java.time.Instant.parse("2026-09-01T00:00:00Z"), java.time.Instant.parse("2026-09-08T16:00:00Z"),
                        "ACTIVE", 3, java.time.Instant.parse("2026-08-01T00:00:00Z"), java.time.Instant.parse("2026-08-27T00:00:00Z"))));
            }
            public CompletableFuture<EmptyResponse> importOutcomes(ImportCourseOutcomesCommand command) {
                return CompletableFuture.completedFuture(EmptyResponse.INSTANCE);
            }
            public CompletableFuture<CourseView> createCourse(CreateCourseCommand command) {
                return CompletableFuture.completedFuture(new CourseView(
                        "preview-created-course", command.courseCode(), command.courseName(), command.credit(),
                        command.totalHours(), command.description(), command.active(), 0,
                        java.time.Instant.now(), java.time.Instant.now()));
            }
            public CompletableFuture<CourseView> updateCourse(UpdateCourseCommand command) {
                return CompletableFuture.completedFuture(new CourseView(
                        command.courseId(), command.courseCode(), command.courseName(), command.credit(),
                        command.totalHours(), command.description(), command.active(), command.expectedVersion() + 1,
                        java.time.Instant.now(), java.time.Instant.now()));
            }
            public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return CompletableFuture.completedFuture(new TermPhaseView(termId, "ACTIVE", "ADJUSTMENT",
                        java.time.Instant.parse("2026-09-03T00:00:00Z"), java.time.Instant.parse("2026-08-20T00:00:00Z"),
                        java.time.Instant.parse("2026-08-31T16:00:00Z"), java.time.Instant.parse("2026-09-01T00:00:00Z"),
                        java.time.Instant.parse("2026-09-08T16:00:00Z")));
            }
        };
    }
}
