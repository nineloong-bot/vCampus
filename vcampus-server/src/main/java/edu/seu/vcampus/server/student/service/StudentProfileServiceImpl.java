package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentProfileApplicationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.user.service.UserQueryPort;

import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Lock-serialized implementation of student profile drafts and administrator reviews. */
public final class StudentProfileServiceImpl implements StudentProfileService {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final StudentRepository students;
    private final StudentProfileApplicationRepository applications;
    private final StudentChangeRepository changes;
    private final UserQueryPort users;

    public StudentProfileServiceImpl(TransactionManager transactions, ResourceLockManager locks,
            StudentRepository students, StudentProfileApplicationRepository applications,
            StudentChangeRepository changes, UserQueryPort users) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.students = Objects.requireNonNull(students);
        this.applications = Objects.requireNonNull(applications);
        this.changes = Objects.requireNonNull(changes);
        this.users = Objects.requireNonNull(users);
    }

    @Override
    public StudentProfileWorkspace getWorkspace(String userId) {
        String campusCard = campusCard(userId);
        return transactions.inTransaction(connection -> {
            StudentProfileData formal = students.findProfileByUserId(connection, userId, campusCard);
            StudentProfileApplicationView application = applications.findOpen(connection,
                    formal.core().studentId()).orElseGet(() -> applications.findLatest(connection,
                            formal.core().studentId()).orElse(null));
            return new StudentProfileWorkspace(formal, application);
        });
    }

    @Override
    public StudentProfileWorkspace savePersonalDraft(String userId,
            SaveStudentPersonalDraftCommand command) {
        Objects.requireNonNull(command); Objects.requireNonNull(command.personal());
        return saveDraft(userId, command.expectedApplicationVersion(), current ->
                new DraftValues(command.personal(), current.attendanceMode()), true);
    }

    @Override
    public StudentProfileWorkspace saveAttendanceDraft(String userId,
            SaveStudentAttendanceDraftCommand command) {
        Objects.requireNonNull(command); Objects.requireNonNull(command.attendanceMode());
        return saveDraft(userId, command.expectedApplicationVersion(), current ->
                new DraftValues(current.personal(), command.attendanceMode()), false);
    }

    private StudentProfileWorkspace saveDraft(String userId, long expectedVersion,
            Function<DraftValues, DraftValues> mutation, boolean personalChange) {
        String studentId = transactions.inTransaction(connection -> students.findByUserId(connection, userId)
                .orElseThrow(StudentNotFoundException::new).studentId());
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    StudentProfileData formal = students.findProfileByStudentId(connection, studentId,
                            campusCard(userId));
                    StudentProfileApplicationView open = applications.findOpen(connection, studentId)
                            .orElse(null);
                    if (open != null && open.status() == StudentProfileApplicationStatus.PENDING)
                        throw new StudentProfileApplicationException("STUDENT_PROFILE_PENDING",
                                "资料申请正在审核，暂不可编辑");
                    DraftValues current = open == null
                            ? new DraftValues(formal.personal(), formal.academic().attendanceMode())
                            : new DraftValues(open.personal(), open.attendanceMode());
                    DraftValues changed = mutation.apply(current);
                    Instant now = Instant.now();
                    if (open == null) {
                        if (expectedVersion != 0) throw new ConcurrentModificationException("Draft version changed");
                        applications.insertDraft(connection, new StudentProfileApplicationView(
                                UUID.randomUUID().toString(), studentId, StudentProfileApplicationStatus.DRAFT,
                                changed.personal(), changed.attendanceMode(), formal.core().rowVersion(), 1,
                                null, null, null, null, now, now));
                    } else if (personalChange) {
                        applications.updatePersonal(connection, open.applicationId(), changed.personal(),
                                expectedVersion, now);
                    } else {
                        applications.updateAttendance(connection, open.applicationId(), changed.attendanceMode(),
                                expectedVersion, now);
                    }
                    StudentProfileApplicationView saved = applications.findOpen(connection, studentId).orElseThrow();
                    return new StudentProfileWorkspace(formal, saved);
                }));
    }

    @Override
    public StudentProfileWorkspace submit(String userId, SubmitStudentProfileCommand command) {
        String studentId = transactions.inTransaction(connection -> students.findByUserId(connection, userId)
                .orElseThrow(StudentNotFoundException::new).studentId());
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    StudentProfileData formal = students.findProfileByStudentId(connection, studentId,
                            campusCard(userId));
                    StudentProfileApplicationView draft = applications.findOpen(connection, studentId)
                            .orElseThrow(() -> new StudentProfileApplicationException(
                                    "STUDENT_PROFILE_DRAFT_NOT_FOUND", "没有可提交的暂存资料"));
                    if (draft.status() != StudentProfileApplicationStatus.DRAFT)
                        throw new StudentProfileApplicationException("STUDENT_PROFILE_PENDING", "资料申请正在审核");
                    if (draft.baseStudentVersion() != formal.core().rowVersion())
                        throw new ConcurrentModificationException("Student profile version changed");
                    if (sameEditableValues(formal, draft))
                        throw new StudentProfileApplicationException("STUDENT_PROFILE_NO_CHANGES", "资料没有发生变化");
                    applications.submit(connection, draft.applicationId(),
                            command.expectedApplicationVersion(), Instant.now());
                    return new StudentProfileWorkspace(formal,
                            applications.findOpen(connection, studentId).orElseThrow());
                }));
    }

    @Override
    public PageResult<StudentProfileApplicationView> listPending(StudentProfileReviewQuery query) {
        if (query.page() < 1 || query.pageSize() < 1 || query.pageSize() > 100)
            throw new IllegalArgumentException("无效分页参数");
        List<StudentProfileApplicationView> all = transactions.inTransaction(applications::listPending);
        int from = Math.min((query.page() - 1) * query.pageSize(), all.size());
        int to = Math.min(from + query.pageSize(), all.size());
        return new PageResult<>(all.subList(from, to), query.page(), query.pageSize(), all.size());
    }

    @Override
    public StudentProfileWorkspace getApplication(String applicationId) {
        return transactions.inTransaction(connection -> {
            StudentProfileApplicationView application = applications.findById(connection, applicationId)
                    .orElseThrow(() -> new StudentProfileApplicationException(
                            "STUDENT_PROFILE_APPLICATION_NOT_FOUND", "资料申请不存在"));
            var student = students.findById(connection, application.studentId())
                    .orElseThrow(StudentNotFoundException::new);
            StudentProfileData formal = students.findProfileByStudentId(connection,
                    application.studentId(), campusCard(student.userId()));
            return new StudentProfileWorkspace(formal, application);
        });
    }

    @Override
    public StudentProfileApplicationView approve(String applicationId, String reviewerUserId,
            String reviewComment) {
        return review(applicationId, reviewerUserId, reviewComment, true);
    }

    @Override
    public StudentProfileApplicationView reject(String applicationId, String reviewerUserId,
            String reviewComment) {
        if (reviewComment == null || reviewComment.isBlank())
            throw new IllegalArgumentException("驳回原因不能为空");
        return review(applicationId, reviewerUserId, reviewComment.trim(), false);
    }

    private StudentProfileApplicationView review(String applicationId, String reviewerUserId,
            String reviewComment, boolean approve) {
        String studentId = transactions.inTransaction(connection -> applications.findById(connection, applicationId)
                .orElseThrow(() -> new StudentProfileApplicationException(
                        "STUDENT_PROFILE_APPLICATION_NOT_FOUND", "资料申请不存在")).studentId());
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    StudentProfileApplicationView pending = applications.findById(connection, applicationId)
                            .filter(value -> value.status() == StudentProfileApplicationStatus.PENDING)
                            .orElseThrow(() -> new StudentProfileApplicationException(
                                    "STUDENT_PROFILE_NOT_PENDING", "资料申请已处理"));
                    Instant now = Instant.now();
                    if (approve) {
                        var student = students.findById(connection, studentId).orElseThrow(StudentNotFoundException::new);
                        if (student.rowVersion() != pending.baseStudentVersion())
                            throw new ConcurrentModificationException("Student profile version changed");
                        students.applyApprovedProfile(connection, pending, now);
                        applications.markApproved(connection, applicationId, reviewerUserId,
                                blankToNull(reviewComment), now);
                        changes.insertChange(connection, UUID.randomUUID().toString(), studentId,
                                "PROFILE_CHANGE", "version=" + pending.baseStudentVersion(),
                                "application=" + applicationId, "学生资料审核通过", reviewerUserId,
                                java.time.LocalDate.now(), now);
                    } else {
                        applications.markRejected(connection, applicationId, reviewerUserId,
                                reviewComment, now);
                    }
                    return applications.findById(connection, applicationId).orElseThrow();
                }));
    }

    private String campusCard(String userId) {
        return users.findByUserId(userId).orElseThrow(() ->
                new IllegalStateException("STUDENT_USER_ACCOUNT_NOT_FOUND")).loginId();
    }

    private static boolean sameEditableValues(StudentProfileData formal,
            StudentProfileApplicationView application) {
        return Objects.equals(formal.personal(), application.personal())
                && formal.academic().attendanceMode() == application.attendanceMode();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record DraftValues(StudentPersonalProfile personal, AttendanceMode attendanceMode) { }
}
