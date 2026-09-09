package edu.seu.vcampus.server.student.majortransfer.repository;

import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MajorTransferRepositoryTest {
    private StudentAccessTestDatabase database;
    private MajorTransferRepository repository;
    private Connection connection;

    private static final Instant NOW = Instant.now();
    private static final Instant YESTERDAY = NOW.minus(1, ChronoUnit.DAYS);
    private static final Instant TOMORROW = NOW.plus(1, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        repository = new MajorTransferRepository();
        database.transactions().inTransaction(conn -> {
            connection = conn;
            insertBaseData(conn);
            return null;
        });
        connection = database.provider().open();
        connection.setAutoCommit(false);
    }

    private void insertBaseData(Connection conn) {
        var organizations = new AccessOrganizationRepository();
        organizations.insertDepartment(conn,
                new Department("dept-1", "CS", "计算机学院", true, 0));
        organizations.insertDepartment(conn,
                new Department("dept-2", "SE", "软件学院", true, 0));
        organizations.insertMajor(conn,
                new Major("major-1", "dept-1", "090", "计算机科学", "1,2,3,4", true, 0));
        organizations.insertMajor(conn,
                new Major("major-2", "dept-2", "085", "软件工程", "1,2,3,4", true, 0));
        organizations.insertClass(conn,
                new StudentClass("class-1", "major-1", "090-24-1", "计科2401", 2024, 1, true, 0));
        organizations.insertClass(conn,
                new StudentClass("class-2", "major-2", "085-24-1", "软工2401", 2024, 1, true, 0));
        new StudentRepository().insert(conn, new Student("student-1", "user-1", "21324001",
                StudentType.UNDERGRADUATE, "张三", "男", "zhang@seu.edu.cn", "13800000000",
                "major-1", "class-1", LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE,
                0, NOW, NOW));
        new StudentRepository().insert(conn, new Student("student-2", "user-2", "21324002",
                StudentType.UNDERGRADUATE, "李四", "男", "li@seu.edu.cn", "13800000001",
                "major-1", "class-1", LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE,
                0, NOW, NOW));
    }

    // ── Batch tests ──

    @Test
    void insertAndFindBatch() {
        String id = repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        MajorTransferRepository.BatchRow found = repository.findBatch(connection, id).orElseThrow();
        assertThat(found.batchName()).isEqualTo("2026年春季转专业");
        assertThat(found.status()).isEqualTo(MajorTransferBatchStatus.OPEN);
    }

    @Test
    void listBatchesReturnsAll() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertBatch(connection, batch("batch-2", MajorTransferBatchStatus.DRAFT));
        assertThat(repository.listBatches(connection)).hasSize(2);
    }

    // ── Option tests ──

    @Test
    void insertAndFindOption() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        String id = repository.insertOption(connection, option("opt-1", "batch-1"));
        MajorTransferRepository.OptionRow found = repository.findOption(connection, id).orElseThrow();
        assertThat(found.targetMajorName()).isEqualTo("软件工程");
        assertThat(found.receiveQuota()).isEqualTo(10);
        assertThat(found.writtenWeightPct()).isEqualTo(60);
        assertThat(found.interviewWeightPct()).isEqualTo(40);
    }

    @Test
    void listOptionsByBatch() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertOption(connection, option("opt-2", "batch-1"));
        assertThat(repository.listOptionsByBatch(connection, "batch-1")).hasSize(2);
    }

    // ── Application tests ──

    @Test
    void insertDraftAndFind() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        String id = repository.insertDraft(connection,
                draft("app-1", "batch-1", "student-1", "opt-1"));
        MajorTransferRepository.ApplicationRow found =
                repository.findApplication(connection, id).orElseThrow();
        assertThat(found.status()).isEqualTo(MajorTransferStatus.DRAFT);
        assertThat(found.fromMajorId()).isEqualTo("major-1");
        assertThat(found.studentName()).isEqualTo("张三");
    }

    @Test
    void preservesSubmissionSnapshot() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        MajorTransferRepository.ApplicationRow row =
                repository.findApplication(connection, "app-1").orElseThrow();
        assertThat(row.fromMajorId()).isEqualTo("major-1");
        assertThat(row.fromDepartmentName()).isEqualTo("计算机学院");
    }

    @Test
    void rejectsSecondApplicationInBatch() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        assertThatThrownBy(() -> repository.insertDraft(connection,
                draft("app-2", "batch-1", "student-1", "opt-1")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void updateDraftFields() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertOption(connection, option("opt-2", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        int updated = repository.updateDraftFields(connection, "app-1", "opt-2",
                MajorTransferApplicationType.DIFFICULTY, "更新理由", 0, NOW);
        assertThat(updated).isEqualTo(1);
        MajorTransferRepository.ApplicationRow row =
                repository.findApplication(connection, "app-1").orElseThrow();
        assertThat(row.optionId()).isEqualTo("opt-2");
        assertThat(row.reason()).isEqualTo("更新理由");
    }

    @Test
    void staleStatusUpdateChangesNoRows() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        int changed = repository.updateApplicationStatus(connection, "app-1",
                MajorTransferStatus.DRAFT, MajorTransferStatus.SUBMITTED, 99, NOW);
        assertThat(changed).isZero();
    }

    @Test
    void submitApplication() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        int changed = repository.submitApplication(connection, "app-1", 0, NOW);
        assertThat(changed).isEqualTo(1);
        MajorTransferRepository.ApplicationRow row =
                repository.findApplication(connection, "app-1").orElseThrow();
        assertThat(row.status()).isEqualTo(MajorTransferStatus.SUBMITTED);
        assertThat(row.submittedAt()).isNotNull();
    }

    @Test
    void listApplicationsByStudent() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        List<MajorTransferRepository.ApplicationRow> apps =
                repository.listApplicationsByStudent(connection, "student-1");
        assertThat(apps).hasSize(1);
    }

    @Test
    void hasSuccessfulTransfer() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        assertThat(repository.hasSuccessfulTransfer(connection, "student-1")).isFalse();
        repository.updateApplicationStatus(connection, "app-1",
                MajorTransferStatus.DRAFT, MajorTransferStatus.SUBMITTED, 1, NOW);
        assertThat(repository.hasSuccessfulTransfer(connection, "student-1")).isFalse();
    }

    // ── Attachment tests ──

    @Test
    void insertAndListAttachments() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        byte[] pdf = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF
        repository.insertAttachment(connection, "att-1", "app-1", "证明.pdf",
                "application/pdf", pdf.length, pdf, NOW);
        assertThat(repository.listAttachments(connection, "app-1")).hasSize(1);
        assertThat(repository.countAttachments(connection, "app-1")).isEqualTo(1);
    }

    @Test
    void deleteAttachment() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        byte[] pdf = new byte[]{0x25, 0x50, 0x44, 0x46};
        repository.insertAttachment(connection, "att-1", "app-1", "证明.pdf",
                "application/pdf", pdf.length, pdf, NOW);
        repository.deleteAttachment(connection, "att-1", "app-1");
        assertThat(repository.listAttachments(connection, "app-1")).isEmpty();
    }

    // ── Review tests ──

    @Test
    void insertAndListReviews() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                "rev-1", "app-1", MajorTransferReviewStage.SOURCE_REVIEW,
                MajorTransferDecision.APPROVE, "admin-1", "通过",
                true, true, true, NOW));
        assertThat(repository.listReviews(connection, "app-1")).hasSize(1);
    }

    @Test
    void rejectsDuplicateStageReview() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                "rev-1", "app-1", MajorTransferReviewStage.SOURCE_REVIEW,
                MajorTransferDecision.APPROVE, "admin-1", "通过",
                true, true, true, NOW));
        assertThatThrownBy(() -> repository.insertReview(connection,
                new MajorTransferRepository.ReviewRow("rev-2", "app-1",
                        MajorTransferReviewStage.SOURCE_REVIEW,
                        MajorTransferDecision.APPROVE, "admin-2", "再次通过",
                        true, true, true, NOW)))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.listReviews(connection, "app-1")).hasSize(1);
    }

    // ── Execution tests ──

    @Test
    void insertAndListExecutions() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        repository.insertExecution(connection, new MajorTransferRepository.ExecutionRow(
                "exec-1", "app-1", "class-2", "软工2401",
                "major-2", "软件工程", "dept-2", "软件学院",
                "PENDING", "admin-1", LocalDate.now(), NOW));
        assertThat(repository.listExecutions(connection, "app-1")).hasSize(1);
    }

    // ── Score recording ──

    @Test
    void recordScores() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        int changed = repository.recordScores(connection, "app-1", 80.0, 90.0, 84.0, 0, NOW);
        assertThat(changed).isEqualTo(1);
        MajorTransferRepository.ApplicationRow row =
                repository.findApplication(connection, "app-1").orElseThrow();
        assertThat(row.writtenScore()).isEqualTo(80.0);
        assertThat(row.interviewScore()).isEqualTo(90.0);
        assertThat(row.finalScore()).isEqualTo(84.0);
    }

    // ── Source review recording ──

    @Test
    void recordSourceReview() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        int changed = repository.recordSourceReview(connection, "app-1", "admin-1",
                true, true, true, "通过", NOW);
        assertThat(changed).isEqualTo(1);
        MajorTransferRepository.ApplicationRow row =
                repository.findApplication(connection, "app-1").orElseThrow();
        assertThat(row.sourceReviewerUserId()).isEqualTo("admin-1");
    }

    // ── Overlapping batch detection ──

    @Test
    void findOverlappingOpenBatches() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        List<MajorTransferRepository.BatchRow> overlapping =
                repository.findOverlappingOpenBatches(connection, YESTERDAY, TOMORROW, null);
        assertThat(overlapping).hasSize(1);
    }

    @Test
    void findOverlappingExcludesSelf() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        List<MajorTransferRepository.BatchRow> overlapping =
                repository.findOverlappingOpenBatches(connection, YESTERDAY, TOMORROW, "batch-1");
        assertThat(overlapping).isEmpty();
    }

    // ── Find by batch+student ──

    @Test
    void findApplicationByBatchStudent() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        repository.insertDraft(connection, draft("app-1", "batch-1", "student-1", "opt-1"));
        assertThat(repository.findApplicationByBatchStudent(connection, "batch-1", "student-1"))
                .isPresent();
        assertThat(repository.findApplicationByBatchStudent(connection, "batch-1", "student-2"))
                .isEmpty();
    }

    // ── Option update ──

    @Test
    void updateOption() {
        repository.insertBatch(connection, batch("batch-1", MajorTransferBatchStatus.OPEN));
        repository.insertOption(connection, option("opt-1", "batch-1"));
        var original = repository.findOption(connection, "opt-1").orElseThrow();
        int changed = repository.updateOption(connection, new MajorTransferRepository.OptionRow(
                "opt-1", "batch-1", "major-2", "dept-2", "软件工程", "软件学院",
                "2024", 20, 15, 70.0, 60.0, 50, 50, false, "新要求", true,
                0, NOW, NOW));
        assertThat(changed).isEqualTo(1);
        var updated = repository.findOption(connection, "opt-1").orElseThrow();
        assertThat(updated.receiveQuota()).isEqualTo(20);
        assertThat(updated.writtenWeightPct()).isEqualTo(50);
    }

    // ── Helpers ──

    private MajorTransferRepository.BatchRow batch(String id, MajorTransferBatchStatus status) {
        return new MajorTransferRepository.BatchRow(id, "2026年春季转专业",
                status, YESTERDAY, TOMORROW, null, null, null, 0, NOW, NOW);
    }

    private MajorTransferRepository.OptionRow option(String id, String batchId) {
        return new MajorTransferRepository.OptionRow(id, batchId, "major-2", "dept-2",
                "软件工程", "软件学院", "2024", 10, 5,
                60.0, 60.0, 60, 40, false, null, true, 0, NOW, NOW);
    }

    private MajorTransferRepository.ApplicationRow draft(String id, String batchId,
            String studentId, String optionId) {
        return new MajorTransferRepository.ApplicationRow(id, batchId, studentId,
                MajorTransferApplicationType.ORDINARY, MajorTransferStatus.DRAFT, optionId,
                "dept-1", "计算机学院", "major-1", "计算机科学",
                "class-1", "计科2401", "21324001", "2024",
                "张三", "希望学习软件工程",
                null, null, null, 0, 0,
                null, null, null, null, null, null, null, NOW, NOW);
    }
}
