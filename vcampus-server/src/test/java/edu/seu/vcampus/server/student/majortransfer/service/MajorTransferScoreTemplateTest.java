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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MajorTransferScoreTemplateTest {
    private StudentAccessTestDatabase database;
    private MajorTransferServiceImpl service;
    private MajorTransferRepository repository;

    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        repository = new MajorTransferRepository();
        var studentRepo = new StudentRepository();
        var orgs = new AccessOrganizationRepository();
        database.transactions().inTransaction(connection -> {
            orgs.insertDepartment(connection, new Department("dept-1", "MATH", "数学院", true, 0));
            orgs.insertDepartment(connection, new Department("dept-2", "CS", "计科学院", true, 0));
            orgs.insertMajor(connection, new Major("major-1", "dept-1", "070", "数学与应用数学", "1,2,3,4", true, 0));
            orgs.insertMajor(connection, new Major("major-2", "dept-2", "090", "计算机科学与技术", "1,2,3,4", true, 0));
            orgs.insertClass(connection, new StudentClass("class-1", "major-1", "070-26-1", "数应2601", 2026, 1, true, 0));
            orgs.insertClass(connection, new StudentClass("class-2", "major-2", "090-26-1", "计科2601", 2026, 1, true, 0));
            studentRepo.insert(connection, new Student("student-1", "user-1", "70125101",
                    StudentType.UNDERGRADUATE, "钱子涵", "男", "qian@seu.edu.cn", "13800000000",
                    "major-1", "class-1", LocalDate.of(2026, 9, 1), StudentStatus.ACTIVE,
                    0, NOW, NOW));
            studentRepo.insert(connection, new Student("student-2", "user-2", "70125102",
                    StudentType.UNDERGRADUATE, "孙八", "男", "sun@seu.edu.cn", "13800000001",
                    "major-1", "class-1", LocalDate.of(2026, 9, 1), StudentStatus.ACTIVE,
                    0, NOW, NOW));
            try (var s = connection.createStatement()) {
                s.executeUpdate("UPDATE tblStudent SET enrolled=1, onCampus=1, birthDate=#2008-09-01#");
            }
            return null;
        });

        UserQueryPort users = new UserQueryPort() {
            @Override public Optional<UserIdentity> findActiveUser(String userId) { return findByUserId(userId); }
            @Override public Optional<UserIdentity> findByUserId(String userId) {
                if ("user-1".equals(userId)) return Optional.of(new UserIdentity(userId, "70125101", UserRole.STUDENT, AccountStatus.ACTIVE));
                if ("user-2".equals(userId)) return Optional.of(new UserIdentity(userId, "70125102", UserRole.STUDENT, AccountStatus.ACTIVE));
                return Optional.empty();
            }
            @Override public Optional<UserIdentity> findByLoginId(String loginId) { return Optional.empty(); }
            @Override public boolean hasRole(String userId, UserRole role) { return role == UserRole.STUDENT; }
        };

        service = new MajorTransferServiceImpl(database.transactions(), new StripedResourceLockManager(),
                repository, studentRepo, new StudentChangeRepository(), orgs, users,
                (connection, studentId, majorCode, cohortYear, operator, occurredAt) ->
                        new MajorTransferEnrollmentPort.Reconciliation(1));

        database.transactions().inTransaction(connection -> {
            repository.insertBatch(connection, new MajorTransferRepository.BatchRow(
                    "batch-1", "2026秋季转专业", MajorTransferBatchStatus.OPEN,
                    NOW.minus(2, ChronoUnit.DAYS), NOW.plus(2, ChronoUnit.DAYS),
                    NOW.plus(3, ChronoUnit.DAYS), NOW.plus(7, ChronoUnit.DAYS),
                    NOW.plus(10, ChronoUnit.DAYS), 0, NOW, NOW));
            repository.insertOption(connection, new MajorTransferRepository.OptionRow(
                    "opt-1", "batch-1", "major-2", "dept-2", "计算机科学与技术", "计科学院",
                    "2026", 10, 5, 60.0, 60.0, 50, 50, false, null, true, 0, NOW, NOW));
            return null;
        });
    }

    @Test
    void exportScoreTemplateReturnsCsvWithQualifiedStudentsAndSupportsImport() {
        var draft = service.saveDraft("user-1", new SaveMajorTransferDraftCommand(
                null, "batch-1", "opt-1", MajorTransferApplicationType.ORDINARY,
                "兴趣所在", 0));
        var submitted = service.submit("user-1", new SubmitMajorTransferCommand(draft.applicationId(), draft.applicationVersion()));
        var sourceApproved = service.reviewSource("math-admin", new ReviewMajorTransferSourceCommand(
                submitted.applicationId(), MajorTransferDecision.APPROVE, true, true, true, "同意", submitted.applicationVersion()), "dept-1");
        var qualified = service.reviewQualification("cs-admin", new ReviewMajorTransferQualificationCommand(
                sourceApproved.applicationId(), MajorTransferDecision.APPROVE, "符合考核资格", sourceApproved.applicationVersion()), "dept-2");

        assertThatThrownBy(() -> service.exportScoreTemplate("cs-admin", "opt-1", "dept-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");

        MajorTransferScoreTemplateDocument doc = service.exportScoreTemplate("cs-admin", "opt-1", "dept-2");
        assertThat(doc.fileName()).contains("计算机科学与技术").endsWith(".csv");
        String content = new String(doc.content(), StandardCharsets.UTF_8);

        assertThat(content).startsWith("\uFEFF申请编号,一卡通号,姓名,原学院,原专业,目标专业,笔试成绩,面试成绩");
        assertThat(content).contains(qualified.applicationId());
        assertThat(content).contains("70125101");
        assertThat(content).contains("钱子涵");
        assertThat(content).contains("数学院");
        assertThat(content).contains("数学与应用数学");
        assertThat(content).contains("计算机科学与技术");

        var result = service.importScores("cs-admin", new ImportMajorTransferScoresCommand(
                "opt-1", List.of(new ImportMajorTransferScoresCommand.ScoreEntry(
                qualified.applicationId(), new BigDecimal("88.5"), new BigDecimal("92.0"))), 0), "dept-2");
        assertThat(result.totalEntries()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(0);

        var detail = service.getApplicationDetail(qualified.applicationId());
        assertThat(detail.status()).isEqualTo(MajorTransferStatus.ASSESSED);
        assertThat(detail.writtenScore()).isEqualTo(88.5);
        assertThat(detail.interviewScore()).isEqualTo(92.0);
        assertThat(detail.finalScore()).isEqualTo(90.25);
    }

    @Test
    void importScoresAlsoSupportsStudentIdFallback() {
        var draft = service.saveDraft("user-2", new SaveMajorTransferDraftCommand(
                null, "batch-1", "opt-1", MajorTransferApplicationType.ORDINARY,
                "对计算机感兴趣", 0));
        var submitted = service.submit("user-2", new SubmitMajorTransferCommand(draft.applicationId(), draft.applicationVersion()));
        var sourceApproved = service.reviewSource("math-admin", new ReviewMajorTransferSourceCommand(
                submitted.applicationId(), MajorTransferDecision.APPROVE, true, true, true, "同意", submitted.applicationVersion()), "dept-1");
        service.reviewQualification("cs-admin", new ReviewMajorTransferQualificationCommand(
                sourceApproved.applicationId(), MajorTransferDecision.APPROVE, "符合考核资格", sourceApproved.applicationVersion()), "dept-2");

        var result = service.importScores("cs-admin", new ImportMajorTransferScoresCommand(
                "opt-1", List.of(new ImportMajorTransferScoresCommand.ScoreEntry(
                "70125102", new BigDecimal("80"), new BigDecimal("85"))), 0), "dept-2");
        assertThat(result.totalEntries()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(0);

        var detail = service.getApplicationDetail(draft.applicationId());
        assertThat(detail.status()).isEqualTo(MajorTransferStatus.ASSESSED);
        assertThat(detail.writtenScore()).isEqualTo(80.0);
        assertThat(detail.interviewScore()).isEqualTo(85.0);
    }
}
