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
            orgs.insertClass(connection, new StudentClass("class-1", "major-1", "090-24-1", "计科2401", 2024, 1, true, 0));
            orgs.insertClass(connection, new StudentClass("class-2", "major-2", "085-24-1", "软工2401", 2024, 1, true, 0));
            studentRepo.insert(connection, new Student("student-1", "user-1", "21324001",
                    StudentType.UNDERGRADUATE, "张三", "男", "zhang@seu.edu.cn", "13800000000",
                    "major-1", "class-1", LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE,
                    0, NOW, NOW));
            studentRepo.insert(connection, new Student("student-2", "user-2", "21324002",
                    StudentType.UNDERGRADUATE, "李四", "男", "li@seu.edu.cn", "13800000001",
                    "major-1", "class-1", LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE,
                    0, NOW, NOW));
            return null;
        });
        sql("UPDATE tblStudent SET enrolled=1, onCampus=1");
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
                repository, studentRepo, new StudentChangeRepository(), orgs, users);
    }

    private void seedOpenBatchWithOption() {
        database.transactions().inTransaction(connection -> {
            repository.insertBatch(connection, new MajorTransferRepository.BatchRow(
                    "batch-1", "2026春季转专业", MajorTransferBatchStatus.OPEN,
                    YESTERDAY, TOMORROW, null, null, null, 0, NOW, NOW));
            repository.insertOption(connection, new MajorTransferRepository.OptionRow(
                    "opt-1", "batch-1", "major-2", "dept-2", "软件工程", "软件学院",
                    "2024", 10, 5, 60.0, 60.0, 60, 40, false, null, true, 0, NOW, NOW));
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

    @Test void submitRechecksStudentStatus() {
        var app = draft();
        sql("UPDATE tblStudent SET studentStatus='SUSPENDED' WHERE studentId='student-1'");
        assertThatThrownBy(() -> service.submit("user-1", new SubmitMajorTransferCommand(app.applicationId(), 0)))
                .isInstanceOf(MajorTransferException.class);
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

    @Test void zeroQuotaDoesNotAdmitEveryone() {
        var app = assessed();
        sql("UPDATE tblMajorTransferOption SET receiveQuota=0");
        service.generateProposal("admin", new GenerateMajorTransferProposalCommand("opt-1", 0));
        assertThat(service.getApplicationDetail(app.applicationId()).status()).isEqualTo(MajorTransferStatus.REJECTED);
    }

    @Test void rankingKeepsCutoffTies() {
        var first = assessed();
        var second = service.saveDraft("user-2", new SaveMajorTransferDraftCommand(null, "batch-1", "opt-1",
                MajorTransferApplicationType.ORDINARY, "希望转入", 0));
        second = service.submit("user-2", new SubmitMajorTransferCommand(second.applicationId(), 0));
        second = service.reviewSource("admin", new ReviewMajorTransferSourceCommand(second.applicationId(),
                MajorTransferDecision.APPROVE, true, true, true, "通过", second.applicationVersion()));
        second = service.reviewQualification("admin", new ReviewMajorTransferQualificationCommand(second.applicationId(),
                MajorTransferDecision.APPROVE, "通过", second.applicationVersion()));
        service.recordScore("admin", new RecordMajorTransferScoreCommand(second.applicationId(),
                new java.math.BigDecimal("80"), new java.math.BigDecimal("90"), second.applicationVersion()));
        sql("UPDATE tblMajorTransferOption SET receiveQuota=1");
        var ranking = service.generateProposal("admin", new GenerateMajorTransferProposalCommand("opt-1", 0));
        assertThat(ranking.applicants()).hasSize(2).allMatch(a -> a.proposed());
        assertThat(ranking.cutoffScore()).isEqualTo(84.0);
        assertThatThrownBy(() -> service.generateProposal("admin", new GenerateMajorTransferProposalCommand("opt-1", 0)))
                .isInstanceOf(MajorTransferException.class);
    }

    @Test void missingWeightedScoreIsRejected() {
        var app = assessed();
        sql("UPDATE tblMajorTransferApplication SET applicationStatus='QUALIFIED'");
        assertThatThrownBy(() -> service.recordScore("admin", new RecordMajorTransferScoreCommand(app.applicationId(),
                new java.math.BigDecimal("80"), null, app.applicationVersion())))
                .isInstanceOf(MajorTransferException.class);
    }

    @Test void zeroInterviewQuotaCannotAdmitApplicant() {
        var app = assessed();
        sql("UPDATE tblMajorTransferOption SET interviewQuota=0");
        service.generateProposal("admin", new GenerateMajorTransferProposalCommand("opt-1", 0));
        assertThat(service.getApplicationDetail(app.applicationId()).status()).isEqualTo(MajorTransferStatus.REJECTED);
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

    @Test void difficultyExemptionDoesNotConsumeOrdinaryQuota() {
        var app = assessed();
        sql("UPDATE tblMajorTransferOption SET receiveQuota=0, difficultyQuotaExempt=1");
        sql("UPDATE tblMajorTransferApplication SET applicationType='DIFFICULTY'");
        service.generateProposal("admin", new GenerateMajorTransferProposalCommand("opt-1", 0));
        assertThat(service.getApplicationDetail(app.applicationId()).status()).isEqualTo(MajorTransferStatus.PROPOSED);
    }

    @Test void executeRequiresEffectiveDateAndCannotPartiallyWriteOnStaleVersion() throws Exception {
        var app = assessed();
        service.generateProposal("admin", new GenerateMajorTransferProposalCommand("opt-1", 0));
        app = service.getApplicationDetail(app.applicationId());
        app = service.finalizeProposal("admin", new FinalizeMajorTransferCommand(app.applicationId(), app.applicationVersion()));
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
}
