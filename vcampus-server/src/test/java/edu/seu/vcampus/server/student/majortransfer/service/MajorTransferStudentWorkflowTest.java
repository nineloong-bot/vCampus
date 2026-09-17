package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.student.domain.*;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import edu.seu.vcampus.server.user.service.UserQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class MajorTransferStudentWorkflowTest {
    private StudentAccessTestDatabase database;
    private MajorTransferServiceImpl service;
    private MajorTransferRepository repository;
    private int reconciledEnrollments;
    private boolean reconciliationFails;
    private boolean validationFails;

    private static final Instant NOW = Instant.now();
    private static final Instant YESTERDAY = NOW.minus(1, ChronoUnit.DAYS);
    private static final Instant TOMORROW = NOW.plus(1, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        repository = new MajorTransferRepository();
        var studentRepo = new StudentRepository();
        var orgs = new AccessOrganizationRepository();
        database.transactions().inTransaction(connection -> {
            orgs.insertDepartment(connection, new Department("dept-1", "CS", "计算机学院", true, 0));
            orgs.insertDepartment(connection, new Department("dept-2", "SE", "软件学院", true, 0));
            orgs.insertMajor(connection, new Major("major-1", "dept-1", "090", "计算机科学", "1,2,3,4", true, 0));
            orgs.insertMajor(connection, new Major("major-2", "dept-2", "085", "软件工程", "1,2,3,4", true, 0));
            orgs.insertMajor(connection, new Major("major-3", "dept-1", "086", "人工智能", "1,2,3,4", true, 0));
            orgs.insertMajor(connection, new Major("major-4", "dept-2", "087", "数据科学", "1,2,3,4", true, 0));
            orgs.insertClass(connection, new StudentClass("class-1", "major-1", "090-26-1", "计科2601", 2026, 1, true, 0));
            orgs.insertClass(connection, new StudentClass("class-2", "major-2", "085-26-1", "软工2601", 2026, 1, true, 0));
            orgs.insertClass(connection, new StudentClass("class-4", "major-4", "087-26-1", "数科2601", 2026, 1, true, 0));
            studentRepo.insert(connection, new Student("student-1", "user-1", "21324001",
                    StudentType.UNDERGRADUATE, "张三", "男", "zhang@seu.edu.cn", "13800000000",
                    "major-1", "class-1", LocalDate.of(2026, 9, 1), StudentStatus.ACTIVE,
                    0, NOW, NOW));
            studentRepo.insert(connection, new Student("student-2", "user-2", "21324002",
                    StudentType.UNDERGRADUATE, "李四", "男", "li@seu.edu.cn", "13800000001",
                    "major-1", "class-1", LocalDate.of(2026, 9, 1), StudentStatus.ACTIVE,
                    0, NOW, NOW));
            return null;
        });
        sql("UPDATE tblStudent SET enrolled=1, onCampus=1");
        sql("UPDATE tblStudent SET birthDate=#2008-09-01#");
        assertThat(database.stringValue("SELECT enrolled FROM tblStudent WHERE studentId='student-1'")).isEqualTo("TRUE");
        UserQueryPort users = new UserQueryPort() {
            @Override public Optional<UserIdentity> findActiveUser(String userId) {
                return findByUserId(userId);
            }
            @Override public Optional<UserIdentity> findByUserId(String userId) {
                if ("user-1".equals(userId))
                    return Optional.of(new UserIdentity(userId, "213240001", UserRole.STUDENT, AccountStatus.ACTIVE));
                if ("user-2".equals(userId))
                    return Optional.of(new UserIdentity(userId, "213240002", UserRole.STUDENT, AccountStatus.ACTIVE));
                return Optional.empty();
            }
            @Override public Optional<UserIdentity> findByLoginId(String loginId) {
                return Optional.empty();
            }
            @Override public boolean hasRole(String userId, UserRole role) {
                return role == UserRole.STUDENT;
            }
        };
        service = new MajorTransferServiceImpl(database.transactions(), new StripedResourceLockManager(),
                repository, studentRepo, new StudentChangeRepository(), orgs, users,
                new MajorTransferEnrollmentPort() {
                    @Override public void validate(java.sql.Connection connection,
                            String majorCode, int cohortYear) {
                        if (validationFails) throw new MajorTransferException(
                                "CURRICULUM_NOT_CONFIGURED", "目标专业缺少培养方案");
                    }

                    @Override public Reconciliation reconcile(java.sql.Connection connection,
                            String studentId, String majorCode, int cohortYear,
                            String operator, Instant occurredAt) {
                        if (reconciliationFails) throw new MajorTransferException(
                                "CURRICULUM_NOT_CONFIGURED", "目标专业缺少培养方案");
                        reconciledEnrollments++;
                        return new Reconciliation(1);
                    }
                });
    }

    private void seedOpenBatchWithOption() {
        database.transactions().inTransaction(connection -> {
            repository.insertBatch(connection, new MajorTransferRepository.BatchRow(
                    "batch-1", "2026春季转专业", MajorTransferBatchStatus.OPEN,
                    YESTERDAY, TOMORROW, null, null, null, 0, NOW, NOW));
            repository.insertOption(connection, new MajorTransferRepository.OptionRow(
                    "opt-1", "batch-1", "major-2", "dept-2", "软件工程", "软件学院",
                    "2026", 10, 5, 60.0, 60.0, 60, 40, false, null, true, 0, NOW, NOW));
            return null;
        });
    }

    private void sql(String sql) {
        database.transactions().inTransaction(c -> { try (var s = c.createStatement()) {
            s.executeUpdate(sql);
        } return null; });
    }

    private MajorTransferApplicationView draft() {
        seedOpenBatchWithOption();
        return service.saveDraft("user-1", new SaveMajorTransferDraftCommand(null,
                "batch-1", "opt-1", MajorTransferApplicationType.ORDINARY, "申请理由", 0));
    }

    private MajorTransferApplicationView assessed() {
        var app = draft();
        app = service.submit("user-1", new SubmitMajorTransferCommand(app.applicationId(), app.applicationVersion()));
        app = service.reviewSource("admin", new ReviewMajorTransferSourceCommand(app.applicationId(),
                MajorTransferDecision.APPROVE, true, true, true, "核实通过", app.applicationVersion()));
        app = service.reviewQualification("admin", new ReviewMajorTransferQualificationCommand(app.applicationId(),
                MajorTransferDecision.APPROVE, "符合要求", app.applicationVersion()));
        return service.recordScore("admin", new RecordMajorTransferScoreCommand(app.applicationId(),
                new java.math.BigDecimal("80"), new java.math.BigDecimal("90"), app.applicationVersion()));
    }

    @Test void closedBatchStillShowsSubmittedApplication() {
        var app = draft();
        service.submit("user-1", new SubmitMajorTransferCommand(app.applicationId(), 0));
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED'");
        assertThat(service.getStudentWorkspace("user-1").application()).isNotNull();
    }

    @Test void workspaceOffersFundedSameAndCrossCollegeOptionsButNotCurrentMajor() {
        seedOpenBatchWithOption();
        database.transactions().inTransaction(connection -> {
            repository.insertOption(connection, new MajorTransferRepository.OptionRow(
                    "opt-ai", "batch-1", "major-3", "dept-1", "人工智能", "计算机学院",
                    "2026", 5, 5, 60.0, 60.0, 60, 40, false, null, true, 0, NOW, NOW));
            repository.insertOption(connection, new MajorTransferRepository.OptionRow(
                    "opt-current", "batch-1", "major-1", "dept-1", "计算机科学", "计算机学院",
                    "2026", 5, 5, 60.0, 60.0, 60, 40, false, null, true, 0, NOW, NOW));
            return null;
        });
        sql("UPDATE tblMajorTransferOption SET receiveQuota=0 WHERE optionId='opt-1'");

        assertThat(service.getStudentWorkspace("user-1").availableOptions())
                .extracting(MajorTransferOptionView::optionId)
                .containsExactly("opt-ai");
    }

    @Test void oneStudentCannotCreateTwoApplicationsInTheSameBatch() throws Exception {
        var first = draft();

        assertThatThrownBy(() -> service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, "第二份申请", 0)))
                .isInstanceOf(MajorTransferException.class)
                .extracting(error -> ((MajorTransferException) error).code())
                .isEqualTo("TRANSFER_DUPLICATE_APPLICATION");
        assertThat(database.count("tblMajorTransferApplication")).isOne();
        assertThat(service.saveDraft("user-1", new SaveMajorTransferDraftCommand(
                first.applicationId(), "batch-1", "opt-1",
                MajorTransferApplicationType.ORDINARY, "修改原申请", first.applicationVersion())))
                .extracting(MajorTransferApplicationView::applicationId)
                .isEqualTo(first.applicationId());
    }

    @Test void submitRechecksStudentStatus() {
        var app = draft();
        sql("UPDATE tblStudent SET studentStatus='SUSPENDED' WHERE studentId='student-1'");
        assertThatThrownBy(() -> service.submit("user-1", new SubmitMajorTransferCommand(app.applicationId(), 0)))
                .isInstanceOf(MajorTransferException.class);
    }

    @Test void sameCollegeDifferentMajorCompletesBothCollegeReviews() throws Exception {
        seedOpenBatchWithOption();
        sql("UPDATE tblMajorTransferOption SET targetMajorId='major-3', targetDepartmentId='dept-1', "
                + "targetMajorName='人工智能', targetDepartmentName='计算机学院' WHERE optionId='opt-1'");

        MajorTransferApplicationView application = service.saveDraft("user-1", new SaveMajorTransferDraftCommand(
                null, "batch-1", "opt-1", MajorTransferApplicationType.ORDINARY,
                "希望拓展学习方向", 0));
        assertThat(application.status()).isEqualTo(MajorTransferStatus.DRAFT);
        assertThat(application.targetMajorId()).isEqualTo("major-3");
        assertThat(database.count("tblMajorTransferApplication")).isOne();

        application = service.submit("user-1", new SubmitMajorTransferCommand(
                application.applicationId(), application.applicationVersion()));
        String applicationId = application.applicationId();
        long submittedVersion = application.applicationVersion();
        assertThat(service.listApplicationsForCollege(
                new MajorTransferApplicationQuery("batch-1", null, null), "dept-1"))
                .singleElement().satisfies(item -> {
                    assertThat(item.applicationId()).isEqualTo(applicationId);
                    assertThat(item.sourceApprovalAllowed()).isTrue();
                    assertThat(item.targetApprovalAllowed()).isTrue();
                });
        assertThatThrownBy(() -> service.reviewQualification("college-admin",
                new ReviewMajorTransferQualificationCommand(applicationId,
                        MajorTransferDecision.APPROVE, "通过", submittedVersion), "dept-1"))
                .isInstanceOf(MajorTransferException.class)
                .extracting(error -> ((MajorTransferException) error).code())
                .isEqualTo("TRANSFER_STATE_INVALID");

        application = service.reviewSource("college-admin", new ReviewMajorTransferSourceCommand(
                applicationId, MajorTransferDecision.APPROVE, true, true, true,
                "原专业审核通过", submittedVersion), "dept-1");
        application = service.reviewQualification("college-admin",
                new ReviewMajorTransferQualificationCommand(applicationId,
                        MajorTransferDecision.APPROVE, "目标专业审核通过",
                        application.applicationVersion()), "dept-1");

        assertThat(application.status()).isEqualTo(MajorTransferStatus.QUALIFIED);
        assertThat(application.reviews()).extracting(MajorTransferReviewView::reviewStage)
                .containsExactly(MajorTransferReviewStage.SOURCE_REVIEW,
                        MajorTransferReviewStage.QUALIFICATION_REVIEW);
    }

    @Test void thirdYearStudentIsRejectedBeforeDraftIsPersisted() throws Exception {
        seedOpenBatchWithOption();
        sql("UPDATE tblClass SET enrollmentYear=2024 WHERE classId='class-1'");

        assertThatThrownBy(() -> service.saveDraft("user-1", new SaveMajorTransferDraftCommand(
                null, "batch-1", "opt-1", MajorTransferApplicationType.ORDINARY,
                "希望拓展学习方向", 0)))
                .isInstanceOf(MajorTransferException.class)
                .extracting(error -> ((MajorTransferException) error).code())
                .isEqualTo("TRANSFER_INELIGIBLE");
        assertThat(database.count("tblMajorTransferApplication")).isZero();
    }

    @Test void submitRejectsSameMajorAndWrongGrade() {
        var app = draft();
        sql("UPDATE tblMajorTransferOption SET targetMajorId='major-1', grades='2025'");
        assertThatThrownBy(() -> service.submit("user-1", new SubmitMajorTransferCommand(app.applicationId(), 0)))
                .isInstanceOf(MajorTransferException.class);
    }

    @Test void staleReviewDoesNotAppendAudit() throws Exception {
        var app = draft();
        service.submit("user-1", new SubmitMajorTransferCommand(app.applicationId(), 0));
        assertThatThrownBy(() -> service.reviewSource("admin", new ReviewMajorTransferSourceCommand(
                app.applicationId(), MajorTransferDecision.APPROVE, true, true, true, "通过", 99)))
                .isInstanceOf(java.util.ConcurrentModificationException.class);
        assertThat(database.count("tblMajorTransferReview")).isZero();
    }

    @Test void cannotCancelDraft() {
        var app = draft();
        assertThatThrownBy(() -> service.cancel("admin", new CancelMajorTransferCommand(app.applicationId(), "取消", 0)))
                .isInstanceOf(MajorTransferException.class);
    }

    @Test void scoringEntersAssessedState() {
        assertThat(assessed().status()).isEqualTo(MajorTransferStatus.ASSESSED);
    }

    @Test void closedBatchCannotBeReopened() {
        seedOpenBatchWithOption();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");

        assertThatThrownBy(() -> service.saveBatch("admin", new SaveMajorTransferBatchCommand(
                "batch-1", "2026春季转专业", MajorTransferBatchStatus.OPEN,
                YESTERDAY, TOMORROW, null, null, null, 0)))
                .isInstanceOf(MajorTransferException.class)
                .extracting(error -> ((MajorTransferException) error).code())
                .isEqualTo("TRANSFER_BATCH_CLOSED");
    }

    @Test void sameSchoolWideBatchAllowsIndependentTargetCollegeReadiness() {
        seedOpenBatchWithOption();
        database.transactions().inTransaction(connection -> {
            repository.insertOption(connection, new MajorTransferRepository.OptionRow(
                    "opt-2", "batch-1", "major-3", "dept-1", "人工智能", "计算机学院",
                    "2026", 10, 5, 60.0, 60.0, 60, 40, false, null, true, 0, NOW, NOW));
            return null;
        });
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");

        assertThatCode(() -> service.getBatchReadiness("batch-1", "dept-2"))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.getBatchReadiness("batch-1", "dept-1"))
                .doesNotThrowAnyException();
    }

    @Test void finalReviewTouchesOnlyTheSelectedOption() throws Exception {
        var first = assessed();
        database.transactions().inTransaction(connection -> {
            repository.insertOption(connection, new MajorTransferRepository.OptionRow(
                    "opt-2", "batch-1", "major-4", "dept-2", "数据科学", "软件学院",
                    "2026", 10, 5, 60.0, 60.0, 60, 40, false, null, true, 0, NOW, NOW));
            return null;
        });
        var second = service.saveDraft("user-2", new SaveMajorTransferDraftCommand(null,
                "batch-1", "opt-2", MajorTransferApplicationType.ORDINARY, "申请理由2", 0));
        second = service.submit("user-2", new SubmitMajorTransferCommand(
                second.applicationId(), second.applicationVersion()));
        second = service.reviewSource("admin", new ReviewMajorTransferSourceCommand(
                second.applicationId(), MajorTransferDecision.APPROVE, true, true, true,
                "核实通过", second.applicationVersion()));
        second = service.reviewQualification("admin", new ReviewMajorTransferQualificationCommand(
                second.applicationId(), MajorTransferDecision.APPROVE, "符合要求",
                second.applicationVersion()));
        second = service.recordScore("admin", new RecordMajorTransferScoreCommand(
                second.applicationId(), new java.math.BigDecimal("85"),
                new java.math.BigDecimal("90"), second.applicationVersion()));
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");

        var readiness = service.getOptionReadiness("opt-1", "dept-2");
        var result = service.finalizeOption("admin",
                new FinalizeMajorTransferOptionCommand("opt-1", readiness.optionVersion()), "dept-2");

        assertThat(result.status()).isEqualTo(MajorTransferOptionFinalizationStatus.REVIEWED);
        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + first.applicationId() + "'"))
                .isEqualTo("PENDING_EFFECTIVE");
        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + second.applicationId() + "'"))
                .isEqualTo("ASSESSED");
    }

    @Test void optionWithoutApplicationsCannotBeFinalReviewed() {
        seedOpenBatchWithOption();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");

        var readiness = service.getOptionReadiness("opt-1", "dept-2");

        assertThat(readiness.canReview()).isFalse();
        assertThat(readiness.reason()).isEqualTo("该专业暂无转入申请");
        assertThatThrownBy(() -> service.finalizeOption("admin",
                new FinalizeMajorTransferOptionCommand("opt-1", readiness.optionVersion()), "dept-2"))
                .isInstanceOf(MajorTransferException.class)
                .extracting(error -> ((MajorTransferException) error).code())
                .isEqualTo("TRANSFER_OPTION_NO_APPLICATIONS");
    }

    @Test void optionWithoutAssessedApplicationsCannotBeFinalReviewed() {
        var app = draft();
        app = service.submit("user-1", new SubmitMajorTransferCommand(
                app.applicationId(), app.applicationVersion()));
        service.reviewSource("admin", new ReviewMajorTransferSourceCommand(
                app.applicationId(), MajorTransferDecision.REJECT, true, true, true,
                "不通过", app.applicationVersion()));
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");

        var readiness = service.getOptionReadiness("opt-1", "dept-2");

        assertThat(readiness.canReview()).isFalse();
        assertThat(readiness.reason()).isEqualTo("该专业没有待终审申请");
        assertThatThrownBy(() -> service.finalizeOption("admin",
                new FinalizeMajorTransferOptionCommand("opt-1", readiness.optionVersion()), "dept-2"))
                .isInstanceOf(MajorTransferException.class)
                .extracting(error -> ((MajorTransferException) error).code())
                .isEqualTo("TRANSFER_OPTION_NO_ASSESSED_APPLICATIONS");
    }

    @Test void optionReviewCanBeRolledBackThenReviewedAndEffected() throws Exception {
        var app = assessed();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");
        var reviewed = service.finalizeOption("admin",
                new FinalizeMajorTransferOptionCommand("opt-1", 0), "dept-2");

        var rolledBack = service.rollbackOption("admin",
                new RollbackMajorTransferOptionCommand("opt-1", reviewed.optionVersion()), "dept-2");
        assertThat(rolledBack.status()).isEqualTo(MajorTransferOptionFinalizationStatus.PROCESSING);
        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + app.applicationId() + "'"))
                .isEqualTo("ASSESSED");

        reviewed = service.finalizeOption("admin", new FinalizeMajorTransferOptionCommand(
                "opt-1", rolledBack.optionVersion()), "dept-2");
        var effective = service.effectiveOption("admin", new EffectiveMajorTransferOptionCommand(
                "opt-1", reviewed.optionVersion()), "dept-2");

        assertThat(effective.status()).isEqualTo(MajorTransferOptionFinalizationStatus.EFFECTIVE);
        assertThat(effective.effectiveStudents()).isOne();
        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + app.applicationId() + "'"))
                .isEqualTo("EFFECTIVE");
    }

    @Test void closedBatchRequiresReviewThenOneTimeEffect() throws Exception {
        var app = assessed();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED', effectiveDate=#2027-09-01# "
                + "WHERE batchId='batch-1'");

        var readiness = service.getBatchReadiness("batch-1", "dept-2");
        var result = service.finalizeBatch("admin",
                new FinalizeMajorTransferBatchCommand("batch-1", readiness.collegeVersion()), "dept-2");

        assertThat(result.status()).isEqualTo(MajorTransferCollegeStatus.REVIEWED);
        assertThat(result.preparedApplications()).isEqualTo(1);
        assertThat(result.collegeVersion()).isOne();
        assertThat(database.count("tblMajorTransferPreparedTransfer")).isOne();
        assertThat(database.stringValue("SELECT classId FROM tblStudent WHERE studentId='student-1'"))
                .isEqualTo("class-1");
        var effective = service.effectiveBatch("admin", new EffectiveMajorTransferBatchCommand(
                "batch-1", result.collegeVersion()), "dept-2");
        assertThat(effective.status()).isEqualTo(MajorTransferCollegeStatus.EFFECTIVE);
        assertThat(effective.effectiveStudents()).isEqualTo(1);
        assertThat(effective.droppedEnrollments()).isEqualTo(1);
        assertThat(reconciledEnrollments).isEqualTo(1);
        assertThat(database.stringValue("SELECT classId FROM tblStudent WHERE studentId='student-1'"))
                .isEqualTo("class-2");
        assertThat(database.stringValue("SELECT studentNumber FROM tblStudent WHERE studentId='student-1'"))
                .isEqualTo("08526101");
        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + app.applicationId() + "'")).isEqualTo("EFFECTIVE");
    }

    @Test void finalizeBatchRanksByScoreAndAdmitsTopQuotaWhileRejectingRest() throws Exception {
        var app1 = assessed();
        sql("UPDATE tblMajorTransferOption SET receiveQuota=1 WHERE optionId='opt-1'");
        var app2 = service.saveDraft("user-2", new SaveMajorTransferDraftCommand(null,
                "batch-1", "opt-1", MajorTransferApplicationType.ORDINARY, "申请理由2", 0));
        app2 = service.submit("user-2", new SubmitMajorTransferCommand(app2.applicationId(), app2.applicationVersion()));
        app2 = service.reviewSource("admin", new ReviewMajorTransferSourceCommand(app2.applicationId(),
                MajorTransferDecision.APPROVE, true, true, true, "核实通过", app2.applicationVersion()));
        app2 = service.reviewQualification("admin", new ReviewMajorTransferQualificationCommand(app2.applicationId(),
                MajorTransferDecision.APPROVE, "符合要求", app2.applicationVersion()));
        app2 = service.recordScore("admin", new RecordMajorTransferScoreCommand(app2.applicationId(),
                new java.math.BigDecimal("95"), new java.math.BigDecimal("95"), app2.applicationVersion()));

        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED', effectiveDate=#2027-09-01# WHERE batchId='batch-1'");

        var readiness = service.getBatchReadiness("batch-1", "dept-2");
        assertThat(readiness.canReview()).isTrue();

        var result = service.finalizeBatch("admin",
                new FinalizeMajorTransferBatchCommand("batch-1", readiness.collegeVersion()), "dept-2");

        assertThat(result.status()).isEqualTo(MajorTransferCollegeStatus.REVIEWED);
        assertThat(result.preparedApplications()).isEqualTo(1);

        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + app2.applicationId() + "'")).isEqualTo("PENDING_EFFECTIVE");
        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + app1.applicationId() + "'")).isEqualTo("REJECTED");
    }

    @Test void unresolvedApplicationBlocksWholeBatch() throws Exception {
        var app = draft();
        service.submit("user-1", new SubmitMajorTransferCommand(app.applicationId(), 0));
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");

        var readiness = service.getBatchReadiness("batch-1", "dept-2");

        assertThat(readiness.canReview()).isFalse();
        assertThat(readiness.unresolved()).isOne();
        assertThatThrownBy(() -> service.finalizeBatch("admin",
                new FinalizeMajorTransferBatchCommand("batch-1", readiness.collegeVersion()), "dept-2"))
                .isInstanceOf(MajorTransferException.class);
        assertThat(database.stringValue("SELECT classId FROM tblStudent WHERE studentId='student-1'"))
                .isEqualTo("class-1");
    }

    @Test void unresolvedApplicationBlocksOnlyItsTargetCollege() throws Exception {
        assessed();
        database.transactions().inTransaction(connection -> {
            repository.insertOption(connection, new MajorTransferRepository.OptionRow(
                    "opt-2", "batch-1", "major-3", "dept-1", "人工智能", "计算机学院",
                    "2026", 10, 5, 60.0, 60.0, 60, 40, false, null, true, 0, NOW, NOW));
            return null;
        });
        sql("UPDATE tblStudent SET classId='class-2' WHERE studentId='student-2'");
        var other = service.saveDraft("user-2", new SaveMajorTransferDraftCommand(null,
                "batch-1", "opt-2", MajorTransferApplicationType.ORDINARY, "申请理由", 0));
        service.submit("user-2", new SubmitMajorTransferCommand(
                other.applicationId(), other.applicationVersion()));
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");

        assertThat(service.getBatchReadiness("batch-1", "dept-2").canReview()).isTrue();
        assertThat(service.getBatchReadiness("batch-1", "dept-1").unresolved()).isOne();
    }

    @Test void reviewCanBeRolledBackWithoutDeletingItsAudit() throws Exception {
        var app = assessed();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");
        var reviewed = service.finalizeBatch("admin",
                new FinalizeMajorTransferBatchCommand("batch-1", 0), "dept-2");

        var rolledBack = service.rollbackBatch("admin",
                new RollbackMajorTransferBatchCommand("batch-1", reviewed.collegeVersion()), "dept-2");

        assertThat(rolledBack.status()).isEqualTo(MajorTransferCollegeStatus.PROCESSING);
        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + app.applicationId() + "'")).isEqualTo("ASSESSED");
        assertThat(database.count("tblMajorTransferPreparedTransfer")).isZero();
        assertThat(database.stringValue("SELECT COUNT(*) FROM tblMajorTransferReview WHERE "
                + "applicationId='" + app.applicationId() + "' AND reviewStage IN "
                + "('FINAL_APPROVAL','FINAL_APPROVAL_ROLLBACK')")).isEqualTo("2");
        assertThat(service.getBatchReadiness("batch-1", "dept-2").canReview()).isTrue();
    }

    @Test void effectiveCollegeCannotBeEffectedOrRolledBackAgain() {
        assessed();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");
        var reviewed = service.finalizeBatch("admin",
                new FinalizeMajorTransferBatchCommand("batch-1", 0), "dept-2");
        var effective = service.effectiveBatch("admin", new EffectiveMajorTransferBatchCommand(
                "batch-1", reviewed.collegeVersion()), "dept-2");

        assertThatThrownBy(() -> service.effectiveBatch("admin",
                new EffectiveMajorTransferBatchCommand("batch-1", effective.collegeVersion()), "dept-2"))
                .isInstanceOf(MajorTransferException.class);
        assertThatThrownBy(() -> service.rollbackBatch("admin",
                new RollbackMajorTransferBatchCommand("batch-1", effective.collegeVersion()), "dept-2"))
                .isInstanceOf(MajorTransferException.class);
    }

    @Test void collegeWithNoAdmissionsCanStillReviewAndCloseOnce() {
        seedOpenBatchWithOption();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");

        var reviewed = service.finalizeBatch("admin",
                new FinalizeMajorTransferBatchCommand("batch-1", 0), "dept-2");
        var effective = service.effectiveBatch("admin", new EffectiveMajorTransferBatchCommand(
                "batch-1", reviewed.collegeVersion()), "dept-2");

        assertThat(reviewed.preparedApplications()).isZero();
        assertThat(effective.effectiveStudents()).isZero();
        assertThat(effective.status()).isEqualTo(MajorTransferCollegeStatus.EFFECTIVE);
    }

    @Test void changedStudentSnapshotCannotBeEffected() throws Exception {
        var app = assessed();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");
        var reviewed = service.finalizeBatch("admin",
                new FinalizeMajorTransferBatchCommand("batch-1", 0), "dept-2");
        sql("UPDATE tblStudent SET rowVersion=rowVersion+1 WHERE studentId='student-1'");

        assertThatThrownBy(() -> service.effectiveBatch("admin",
                new EffectiveMajorTransferBatchCommand("batch-1", reviewed.collegeVersion()), "dept-2"))
                .isInstanceOf(MajorTransferException.class);
        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + app.applicationId() + "'"))
                .isEqualTo("PENDING_EFFECTIVE");
        assertThat(database.stringValue("SELECT classId FROM tblStudent WHERE studentId='student-1'"))
                .isEqualTo("class-1");
    }

    @Test void assessedApplicationAppearingAfterReviewBlocksEffectuation() {
        var app = assessed();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");
        var reviewed = service.finalizeBatch("admin",
                new FinalizeMajorTransferBatchCommand("batch-1", 0), "dept-2");
        sql("UPDATE tblMajorTransferApplication SET applicationStatus='ASSESSED' WHERE applicationId='"
                + app.applicationId() + "'");

        assertThat(service.getBatchReadiness("batch-1", "dept-2").canEffect()).isFalse();
        assertThatThrownBy(() -> service.effectiveBatch("admin",
                new EffectiveMajorTransferBatchCommand("batch-1", reviewed.collegeVersion()), "dept-2"))
                .isInstanceOf(MajorTransferException.class)
                .hasMessageContaining("未处理完毕");
    }

    @Test void reviewPreflightsCurriculumBeforeChangingApplicationState() throws Exception {
        var app = assessed();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");
        validationFails = true;

        assertThatThrownBy(() -> service.finalizeBatch("admin",
                new FinalizeMajorTransferBatchCommand("batch-1", 0), "dept-2"))
                .isInstanceOf(MajorTransferException.class)
                .extracting(error -> ((MajorTransferException) error).code())
                .isEqualTo("CURRICULUM_NOT_CONFIGURED");
        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + app.applicationId() + "'")).isEqualTo("ASSESSED");
        assertThat(database.count("tblMajorTransferPreparedTransfer")).isZero();
    }

    @Test void reconciliationFailureRollsBackStudentNumberAndApplication() throws Exception {
        var app = assessed();
        sql("UPDATE tblMajorTransferBatch SET batchStatus='CLOSED' WHERE batchId='batch-1'");
        reconciliationFails = true;

        var reviewed = service.finalizeBatch("admin",
                new FinalizeMajorTransferBatchCommand("batch-1", 0), "dept-2");
        assertThatThrownBy(() -> service.effectiveBatch("admin",
                new EffectiveMajorTransferBatchCommand("batch-1", reviewed.collegeVersion()), "dept-2"))
                .isInstanceOf(MajorTransferException.class)
                .extracting(error -> ((MajorTransferException) error).code())
                .isEqualTo("CURRICULUM_NOT_CONFIGURED");
        assertThat(database.stringValue("SELECT classId FROM tblStudent WHERE studentId='student-1'"))
                .isEqualTo("class-1");
        assertThat(database.stringValue("SELECT studentNumber FROM tblStudent WHERE studentId='student-1'"))
                .isEqualTo("21324001");
        assertThat(database.stringValue("SELECT applicationStatus FROM tblMajorTransferApplication "
                + "WHERE applicationId='" + app.applicationId() + "'")).isEqualTo("PENDING_EFFECTIVE");
        assertThat(database.sequenceValue("STUDENT_NUMBER:085:26:1")).isZero();
    }

    @Test void missingWeightedScoreIsRejected() {
        var app = assessed();
        sql("UPDATE tblMajorTransferApplication SET applicationStatus='QUALIFIED'");
        assertThatThrownBy(() -> service.recordScore("admin", new RecordMajorTransferScoreCommand(app.applicationId(),
                new java.math.BigDecimal("80"), null, app.applicationVersion())))
                .isInstanceOf(MajorTransferException.class);
    }

    @Test void explicitOffCampusStatusPreventsSubmission() {
        var app = draft();
        sql("UPDATE tblStudent SET onCampus=0 WHERE studentId='student-1'");
        assertThatThrownBy(() -> service.submit("user-1", new SubmitMajorTransferCommand(app.applicationId(), 0)))
                .isInstanceOf(MajorTransferException.class);
    }

    @Test void zeroWeightDoesNotRequireUnusedScore() {
        assertThat(MajorTransferServiceImpl.finalScore(80.0, null, 100, 0)).isEqualTo(80.0);
    }

    @Test void staleAttachmentDoesNotWrite() {
        var app = draft();
        assertThatThrownBy(() -> service.uploadAttachment("user-1", new UploadMajorTransferAttachmentCommand(
                app.applicationId(), "证明.pdf", "application/pdf", "%PDF-1.4".getBytes(), 99)))
                .isInstanceOf(java.util.ConcurrentModificationException.class);
        assertThat(service.getApplicationDetail(app.applicationId()).attachments()).isEmpty();
    }

    @Test void assessedApplicationCanReceiveFinalApproval() {
        var app = assessed();
        var finalized = service.finalizeApproval("admin",
                new FinalizeMajorTransferCommand(app.applicationId(), app.applicationVersion()));
        assertThat(finalized.status()).isEqualTo(MajorTransferStatus.PENDING_EFFECTIVE);
    }

    @Test void executeRequiresEffectiveDateAndCannotPartiallyWriteOnStaleVersion() throws Exception {
        var app = assessed();
        app = service.finalizeApproval("admin", new FinalizeMajorTransferCommand(app.applicationId(), app.applicationVersion()));
        String id = app.applicationId();
        long version = app.applicationVersion();
        assertThatThrownBy(() -> service.execute("admin", new ExecuteMajorTransferCommand(id, "class-2", version)))
                .isInstanceOf(MajorTransferException.class);
        sql("UPDATE tblMajorTransferBatch SET effectiveDate=#2020-01-01#");
        assertThatThrownBy(() -> service.execute("admin", new ExecuteMajorTransferCommand(id, "class-2", 99)))
                .isInstanceOf(java.util.ConcurrentModificationException.class);
        assertThat(database.stringValue("SELECT classId FROM tblStudent WHERE studentId='student-1'")).isEqualTo("class-1");
        assertThat(database.count("tblMajorTransferExecution")).isZero();
        service.execute("admin", new ExecuteMajorTransferCommand(id, "class-2", version));
        assertThat(database.stringValue("SELECT classId FROM tblStudent WHERE studentId='student-1'")).isEqualTo("class-2");
        assertThat(database.count("tblMajorTransferExecution")).isEqualTo(1);
    }

    @Test
    void trustedTargetDepartmentIsRecheckedInsideEveryTargetMutation() {
        var app = draft();
        app = service.submit("user-1", new SubmitMajorTransferCommand(
                app.applicationId(), app.applicationVersion()));
        app = service.reviewSource("source-admin", new ReviewMajorTransferSourceCommand(
                app.applicationId(), MajorTransferDecision.APPROVE, true, true, true,
                "通过", app.applicationVersion()), "dept-1");
        String applicationId = app.applicationId();
        long qualificationVersion = app.applicationVersion();

        assertThatThrownBy(() -> service.reviewQualification("wrong-admin",
                new ReviewMajorTransferQualificationCommand(applicationId,
                        MajorTransferDecision.APPROVE, "通过", qualificationVersion), "dept-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
        app = service.reviewQualification("target-admin",
                new ReviewMajorTransferQualificationCommand(applicationId,
                        MajorTransferDecision.APPROVE, "通过", qualificationVersion), "dept-2");
        long scoreVersion = app.applicationVersion();
        var score = new RecordMajorTransferScoreCommand(applicationId,
                new java.math.BigDecimal("80"), new java.math.BigDecimal("90"), scoreVersion);
        assertThatThrownBy(() -> service.recordScore("wrong-admin", score, "dept-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
        app = service.recordScore("target-admin", score, "dept-2");
        long finalVersion = app.applicationVersion();
        assertThatThrownBy(() -> service.finalizeApproval("wrong-admin",
                new FinalizeMajorTransferCommand(applicationId, finalVersion), "dept-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
        app = service.finalizeApproval("target-admin",
                new FinalizeMajorTransferCommand(applicationId, finalVersion), "dept-2");
        sql("UPDATE tblMajorTransferBatch SET effectiveDate=#2020-01-01#");
        long executeVersion = app.applicationVersion();
        assertThatThrownBy(() -> service.execute("wrong-admin",
                new ExecuteMajorTransferCommand(applicationId, "class-2", executeVersion),
                "dept-1")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
        assertThat(service.execute("target-admin",
                new ExecuteMajorTransferCommand(applicationId, "class-2", executeVersion),
                "dept-2").status()).isEqualTo(MajorTransferStatus.EFFECTIVE);
    }

    @Test
    void trustedDepartmentRejectsForgedOptionAndScoreImport() {
        seedOpenBatchWithOption();
        var optionCommand = new SaveMajorTransferOptionCommand(null, "batch-1", "major-2",
                "2024", 10, 5, 60.0, 60.0, 60, 40, false, null, true, 0);
        assertThatThrownBy(() -> service.saveOption("wrong-admin", optionCommand, "dept-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");

        var app = service.saveDraft("user-1", new SaveMajorTransferDraftCommand(null,
                "batch-1", "opt-1", MajorTransferApplicationType.ORDINARY, "申请理由", 0));
        app = service.submit("user-1", new SubmitMajorTransferCommand(
                app.applicationId(), app.applicationVersion()));
        app = service.reviewSource("source-admin", new ReviewMajorTransferSourceCommand(
                app.applicationId(), MajorTransferDecision.APPROVE, true, true, true,
                "通过", app.applicationVersion()), "dept-1");
        app = service.reviewQualification("target-admin",
                new ReviewMajorTransferQualificationCommand(app.applicationId(),
                        MajorTransferDecision.APPROVE, "通过", app.applicationVersion()), "dept-2");
        var importCommand = new ImportMajorTransferScoresCommand("opt-1", java.util.List.of(
                new ImportMajorTransferScoresCommand.ScoreEntry(app.applicationId(),
                        new java.math.BigDecimal("80"), new java.math.BigDecimal("90"))), 0);
        assertThatThrownBy(() -> service.importScores("wrong-admin", importCommand, "dept-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void workspaceShowsActiveBatchAndEligibility() {
        seedOpenBatchWithOption();
        MajorTransferWorkspace workspace = service.getStudentWorkspace("user-1");
        assertThat(workspace.activeBatch()).isNotNull();
        assertThat(workspace.activeBatch().batchName()).isEqualTo("2026春季转专业");
        assertThat(workspace.availableOptions()).hasSize(1);
        assertThat(workspace.eligibilityItems()).isNotEmpty();
        assertThat(workspace.eligibilityItems().stream().allMatch(MajorTransferEligibilityItem::passed)).isTrue();
    }

    @Test
    void workspaceAllowsStudentWhenBatchContainsBothSameAndCrossCollegeOptions() {
        seedOpenBatchWithOption();
        database.transactions().inTransaction(connection -> {
            repository.insertOption(connection, new MajorTransferRepository.OptionRow(
                    "opt-ai", "batch-1", "major-3", "dept-1", "人工智能", "计算机学院",
                    "2026", 5, 5, 60.0, 60.0, 60, 40, false, null, true, 0, NOW, NOW));
            return null;
        });

        MajorTransferWorkspace workspace = service.getStudentWorkspace("user-1");

        assertThat(workspace.eligibilityItems().stream().allMatch(MajorTransferEligibilityItem::passed)).isTrue();
        assertThat(workspace.availableOptions())
                .extracting(MajorTransferOptionView::targetMajorId)
                .containsExactlyInAnyOrder("major-2", "major-3");
    }

    @Test
    void saveDraftCreatesNewApplication() {
        seedOpenBatchWithOption();
        MajorTransferApplicationView app = service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, "希望学习软件工程", 0));
        assertThat(app.status()).isEqualTo(MajorTransferStatus.DRAFT);
        assertThat(app.fromMajorId()).isEqualTo("major-1");
        assertThat(app.fromMajorName()).isEqualTo("计算机科学");
        assertThat(app.studentName()).isEqualTo("张三");
    }

    @Test
    void saveDraftUpdatesExistingDraft() {
        seedOpenBatchWithOption();
        MajorTransferApplicationView created = service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, "初始理由", 0));
        MajorTransferApplicationView updated = service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(created.applicationId(), "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, "更新理由", 0));
        assertThat(updated.reason()).isEqualTo("更新理由");
    }

    @Test
    void submitUsesServerSideIdentityAndSourceSnapshot() {
        seedOpenBatchWithOption();
        MajorTransferApplicationView draft = service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, "希望学习软件工程", 0));
        MajorTransferApplicationView submitted = service.submit("user-1",
                new SubmitMajorTransferCommand(draft.applicationId(), 0));
        assertThat(submitted.status()).isEqualTo(MajorTransferStatus.SUBMITTED);
        assertThat(submitted.studentId()).isEqualTo("student-1");
        assertThat(submitted.fromMajorId()).isEqualTo("major-1");
    }

    @Test
    void submitRejectsBlankReason() {
        seedOpenBatchWithOption();
        MajorTransferApplicationView draft = service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, null, 0));
        assertThatThrownBy(() -> service.submit("user-1",
                new SubmitMajorTransferCommand(draft.applicationId(), 0)))
                .isInstanceOf(MajorTransferException.class)
                .hasMessageContaining("申请理由不能为空");
    }

    @Test
    void withdrawalPreservesDraftAndReason() {
        seedOpenBatchWithOption();
        MajorTransferApplicationView draft = service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, "希望学习软件工程", 0));
        MajorTransferApplicationView submitted = service.submit("user-1",
                new SubmitMajorTransferCommand(draft.applicationId(), 0));
        MajorTransferApplicationView withdrawn = service.withdraw("user-1",
                new WithdrawMajorTransferCommand(submitted.applicationId(), 1));
        assertThat(withdrawn.status()).isEqualTo(MajorTransferStatus.DRAFT);
        assertThat(withdrawn.reason()).isEqualTo("希望学习软件工程");
    }

    @Test
    void uploadAttachmentValidatesMagicBytes() {
        seedOpenBatchWithOption();
        MajorTransferApplicationView draft = service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, "理由", 0));
        byte[] pdf = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E};
        MajorTransferApplicationView updated = service.uploadAttachment("user-1",
                new UploadMajorTransferAttachmentCommand(draft.applicationId(),
                        "证明.pdf", "application/pdf", pdf, 0));
        assertThat(updated.attachments()).hasSize(1);
    }

    @Test
    void uploadRejectsInvalidMagicBytes() {
        seedOpenBatchWithOption();
        MajorTransferApplicationView draft = service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, "理由", 0));
        byte[] fake = new byte[]{0x00, 0x01, 0x02, 0x03};
        assertThatThrownBy(() -> service.uploadAttachment("user-1",
                new UploadMajorTransferAttachmentCommand(draft.applicationId(),
                        "fake.txt", "text/plain", fake, 1)))
                .isInstanceOf(MajorTransferException.class)
                .hasMessageContaining("仅支持PDF、JPEG、PNG");
    }

    @Test
    void rejectsDuplicateApplicationInBatch() {
        seedOpenBatchWithOption();
        service.saveDraft("user-1", new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                MajorTransferApplicationType.ORDINARY, "理由", 0));
        assertThatThrownBy(() -> service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, "再次申请", 0)))
                .isInstanceOf(MajorTransferException.class)
                .hasMessageContaining("进行中");
    }

    @Test
    void studentCannotWithdrawAfterSourceApproval() {
        seedOpenBatchWithOption();
        MajorTransferApplicationView draft = service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.ORDINARY, "理由", 0));
        service.submit("user-1", new SubmitMajorTransferCommand(draft.applicationId(), 0));
        database.transactions().inTransaction(connection -> {
            repository.updateApplicationStatus(connection, draft.applicationId(),
                    MajorTransferStatus.SUBMITTED, MajorTransferStatus.SOURCE_APPROVED, 1, Instant.now());
            return null;
        });
        assertThatThrownBy(() -> service.withdraw("user-1",
                new WithdrawMajorTransferCommand(draft.applicationId(), 2)))
                .isInstanceOf(MajorTransferException.class)
                .hasMessageContaining("不允许撤回");
    }

    @Test
    void difficultyTypeRequiresAttachment() {
        seedOpenBatchWithOption();
        MajorTransferApplicationView draft = service.saveDraft("user-1",
                new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                        MajorTransferApplicationType.DIFFICULTY, "学困理由", 0));
        assertThatThrownBy(() -> service.submit("user-1",
                new SubmitMajorTransferCommand(draft.applicationId(), 0)))
                .isInstanceOf(MajorTransferException.class)
                .hasMessageContaining("证明材料");
    }

    @Test
    void sophomoreCanOnlyDowngradeTransfer() throws Exception {
        database.transactions().inTransaction(connection -> {
            var orgs = new AccessOrganizationRepository();
            orgs.insertClass(connection, new StudentClass("class-math-2025", "major-1", "090-25-1", "数学2501", 2025, 1, true, 0));
            orgs.insertClass(connection, new StudentClass("class-se-2025", "major-2", "085-25-1", "软工2501", 2025, 1, true, 0));
            return null;
        });
        sql("UPDATE tblStudent SET classId='class-math-2025' WHERE studentId='student-1'");
        var app = assessed();
        app = service.finalizeApproval("admin", new FinalizeMajorTransferCommand(app.applicationId(), app.applicationVersion()));
        sql("UPDATE tblMajorTransferBatch SET effectiveDate=#2020-01-01#");
        String id = app.applicationId();
        long version = app.applicationVersion();

        assertThatThrownBy(() -> service.execute("admin", new ExecuteMajorTransferCommand(id, "class-se-2025", version)))
                .isInstanceOf(MajorTransferException.class)
                .hasMessageContaining("降转");

        service.execute("admin", new ExecuteMajorTransferCommand(id, "class-2", version));
        assertThat(database.stringValue("SELECT classId FROM tblStudent WHERE studentId='student-1'")).isEqualTo("class-2");
    }

    @Test
    void saveOptionUpdatesExistingQuotasAndLocksExamWeightsOnSubmittedApplications() {
        seedOpenBatchWithOption();
        var updateCommand = new SaveMajorTransferOptionCommand(null, "batch-1", "major-2",
                "2026", 15, 25, 60.0, 60.0, 60, 40, false, null, true, 0);
        var updated = service.saveOption("admin", updateCommand, "dept-2");
        assertThat(updated.optionId()).isEqualTo("opt-1");
        assertThat(updated.receiveQuota()).isEqualTo(15);
        assertThat(updated.interviewQuota()).isEqualTo(25);
        assertThat(service.listOptions("batch-1")).hasSize(1);

        var app = service.saveDraft("user-1", new SaveMajorTransferDraftCommand(null,
                "batch-1", "opt-1", MajorTransferApplicationType.ORDINARY, "申请理由", 0));
        service.submit("user-1", new SubmitMajorTransferCommand(app.applicationId(), app.applicationVersion()));

        var adjustQuotaCommand = new SaveMajorTransferOptionCommand("opt-1", "batch-1", "major-2",
                "2026", 20, 30, 60.0, 60.0, 60, 40, false, null, true, updated.rowVersion());
        var adjusted = service.saveOption("admin", adjustQuotaCommand, "dept-2");
        assertThat(adjusted.receiveQuota()).isEqualTo(20);
        assertThat(adjusted.interviewQuota()).isEqualTo(30);

        var modifyWeightsCommand = new SaveMajorTransferOptionCommand("opt-1", "batch-1", "major-2",
                "2026", 20, 30, 60.0, 60.0, 70, 30, false, null, true, adjusted.rowVersion());
        assertThatThrownBy(() -> service.saveOption("admin", modifyWeightsCommand, "dept-2"))
                .isInstanceOf(MajorTransferException.class)
                .hasMessageContaining("已锁定");
    }
}
