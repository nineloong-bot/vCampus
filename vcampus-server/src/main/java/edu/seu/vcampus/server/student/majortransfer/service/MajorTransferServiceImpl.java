package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.integration.AccessMajorTransferEnrollmentAdapter;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;
import edu.seu.vcampus.server.student.numbering.AccessStudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.service.StudentNotFoundException;
import edu.seu.vcampus.server.user.service.UserQueryPort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus.*;

/** Full major-transfer workflow implementation. */
public final class MajorTransferServiceImpl implements MajorTransferService {
    private static final int MAX_ATTACHMENTS = 3;
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5 MiB
    private static final int MAX_REASON_LENGTH = 2000;

    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final MajorTransferRepository repository;
    private final StudentRepository students;
    private final StudentChangeRepository changes;
    private final AccessOrganizationRepository organizations;
    private final UserQueryPort users;
    private final MajorTransferBatchFinalizer batchFinalizer;
    private final MajorTransferScoreTemplateExporter scoreTemplateExporter;
    private final MajorTransferEligibilityPolicy eligibilityPolicy =
            new MajorTransferEligibilityPolicy();

    public MajorTransferServiceImpl(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        this(transactions, locks, repository, students, changes, organizations, users,
                new AccessMajorTransferEnrollmentAdapter());
    }

    /** Creates the service with an explicit enrollment port for composition and tests. */
    public MajorTransferServiceImpl(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users,
                                     MajorTransferEnrollmentPort enrollmentPort) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.repository = Objects.requireNonNull(repository);
        this.students = Objects.requireNonNull(students);
        this.changes = Objects.requireNonNull(changes);
        this.organizations = organizations;
        this.users = Objects.requireNonNull(users);
        this.scoreTemplateExporter = new MajorTransferScoreTemplateExporter(transactions, repository);
        this.batchFinalizer = new MajorTransferBatchFinalizer(transactions, locks, repository,
                students, changes, organizations,
                new AccessStudentNumberGenerator(new NumberSequenceRepository()), enrollmentPort);
    }

    // ── Student operations ──

    @Override
    public MajorTransferWorkspace getStudentWorkspace(String userId) {
        return transactions.inTransaction(connection -> {
            Student student = students.findByUserId(connection, userId)
                    .orElseThrow(StudentNotFoundException::new);
            MajorTransferBatchView activeBatch = repository.findOpenBatchAt(connection, Instant.now())
                    .map(this::toBatchView).orElse(null);
            // An application remains visible throughout review, even after registration closes.
            var history = repository.listApplicationsByStudent(connection, student.studentId());
            var ongoing = history.stream().filter(a -> a.status() != REJECTED
                    && a.status() != CANCELLED && a.status() != EFFECTIVE).findFirst();
            if (ongoing.isPresent() || (activeBatch == null && !history.isEmpty())) {
                String batchId = ongoing.orElseGet(() -> history.get(0)).batchId();
                activeBatch = repository.findBatch(connection, batchId).map(this::toBatchView).orElse(null);
            }
            if (activeBatch == null) {
                return emptyWorkspace(student);
            }
            List<MajorTransferOptionView> options = repository.listOptionsByBatch(connection,
                    activeBatch.batchId()).stream()
                    .filter(MajorTransferRepository.OptionRow::active)
                    .map(this::toOptionView).toList();
            List<MajorTransferEligibilityItem> eligibility = checkEligibility(connection, student,
                    activeBatch, options);
            MajorTransferApplicationView application = repository.findApplicationByBatchStudent(
                    connection, activeBatch.batchId(), student.studentId())
                    .map(row -> toApplicationView(connection, row)).orElse(null);
            String deptId = null, deptName = null, majorName = null;
            if (organizations != null) {
                var major = organizations.findMajor(connection, student.majorId());
                if (major.isPresent()) {
                    majorName = major.get().majorName();
                    var dept = organizations.findDepartment(connection, major.get().departmentId());
                    if (dept.isPresent()) {
                        deptId = dept.get().departmentId();
                        deptName = dept.get().departmentName();
                    }
                }
            }
            String className = null;
            if (organizations != null) {
                var cls = organizations.findClass(connection, student.classId());
                if (cls.isPresent()) className = cls.get().className();
            }
            return new MajorTransferWorkspace(activeBatch, options, eligibility,
                    deptId, deptName, student.majorId(), majorName,
                    student.classId(), className, student.studentNumber(),
                    organizations == null ? null : Integer.toString(organizations.findClass(connection, student.classId()).orElseThrow().enrollmentYear()), application);
        });
    }

    @Override
    public MajorTransferApplicationView saveDraft(String userId, SaveMajorTransferDraftCommand command) {
        String studentId = resolveStudentId(userId);
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    Instant now = Instant.now();
                    Student student = students.findByUserId(connection, userId)
                            .orElseThrow(StudentNotFoundException::new);
                    MajorTransferRepository.BatchRow batch = repository.findBatch(connection, command.batchId())
                            .orElseThrow(() -> error("TRANSFER_BATCH_NOT_FOUND", "批次不存在"));
                    validateBatchOpen(batch, now);
                    MajorTransferRepository.OptionRow option = repository.findOption(connection, command.optionId())
                            .orElseThrow(() -> error("TRANSFER_OPTION_NOT_FOUND", "目标专业不存在"));
                    if (!option.batchId().equals(batch.batchId()))
                        throw error("TRANSFER_INVALID_TARGET", "目标专业不属于该批次");
                    if (!option.active())
                        throw error("TRANSFER_OPTION_INACTIVE", "目标专业未启用");
                    validateEligibility(connection, student, batch, option);

                    if (command.applicationId() != null) {
                        // Update existing draft
                        MajorTransferRepository.ApplicationRow existing =
                                repository.findApplication(connection, command.applicationId())
                                        .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                        if (!existing.studentId().equals(student.studentId()))
                            throw error("COMMON_FORBIDDEN", "无权操作");
                        if (!existing.batchId().equals(batch.batchId()))
                            throw error("TRANSFER_INVALID_TARGET", "不能修改申请所属批次");
                        if (!MajorTransferStateMachine.studentMayEdit(existing.status()))
                            throw error("TRANSFER_STATE_INVALID", "当前状态不允许编辑");
                        requireChanged(repository.updateDraftFields(connection, command.applicationId(),
                                command.optionId(), command.applicationType(), command.reason(),
                                command.expectedVersion(), now));
                    } else {
                        // Create new draft
                        repository.findApplicationByBatchStudent(connection, batch.batchId(),
                                student.studentId()).ifPresent(existing -> {
                            if (existing != null) {
                                throw error("TRANSFER_DUPLICATE_APPLICATION", "该批次已有进行中的申请");
                            }
                        });
                        if (repository.hasSuccessfulTransfer(connection, student.studentId()))
                            throw error("TRANSFER_ALREADY_TRANSFERRED", "已有生效的转专业记录");
                        String appType = command.applicationType() == MajorTransferApplicationType.DIFFICULTY
                                ? "DIFFICULTY" : "ORDINARY";
                        String fromDeptId = null, fromDeptName = null, fromMajorName = null;
                        String fromClassName = null;
                        if (organizations != null) {
                            var major = organizations.findMajor(connection, student.majorId());
                            if (major.isPresent()) {
                                fromMajorName = major.get().majorName();
                                var dept = organizations.findDepartment(connection, major.get().departmentId());
                                if (dept.isPresent()) {
                                    fromDeptId = dept.get().departmentId();
                                    fromDeptName = dept.get().departmentName();
                                }
                            }
                            var cls = organizations.findClass(connection, student.classId());
                            if (cls.isPresent()) fromClassName = cls.get().className();
                        }
                        MajorTransferRepository.ApplicationRow draft =
                                new MajorTransferRepository.ApplicationRow(
                                        UUID.randomUUID().toString(), batch.batchId(),
                                        student.studentId(),
                                        command.applicationType(), DRAFT, command.optionId(),
                                        fromDeptId != null ? fromDeptId : "",
                                        fromDeptName != null ? fromDeptName : "",
                                        student.majorId(),
                                        fromMajorName != null ? fromMajorName : "",
                                        student.classId(),
                                        fromClassName != null ? fromClassName : "",
                                        student.studentNumber(), Integer.toString(organizations.findClass(connection, student.classId()).orElseThrow().enrollmentYear()),
                                        student.studentName(), command.reason(),
                                        null, null, null,
                                        student.rowVersion(), 0,
                                        null, null, null, null, null, null, null,
                                        now, now);
                        repository.insertDraft(connection, draft);
                    }
                    MajorTransferRepository.ApplicationRow saved =
                            repository.findApplicationByBatchStudent(connection, batch.batchId(),
                                    student.studentId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    return toApplicationView(connection, saved);
                }));
    }

    @Override
    public MajorTransferApplicationView uploadAttachment(String userId,
                                                          UploadMajorTransferAttachmentCommand command) {
        String studentId = resolveStudentId(userId);
        validateAttachment(command.content(), command.contentType(), command.fileName());
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    if (!app.studentId().equals(studentId))
                        throw error("COMMON_FORBIDDEN", "无权操作");
                    if (!MajorTransferStateMachine.studentMayEdit(app.status()))
                        throw error("TRANSFER_STATE_INVALID", "当前状态不允许编辑");
                    int count = repository.countAttachments(connection, app.applicationId());
                    if (app.applicationVersion() != command.expectedVersion()) throw concurrent();
                    if (count >= MAX_ATTACHMENTS)
                        throw error("TRANSFER_ATTACHMENT_LIMIT", "最多上传" + MAX_ATTACHMENTS + "个附件");
                    Instant now = Instant.now();
                    String contentType = detectContentType(command.content(), command.contentType());
                    repository.insertAttachment(connection, UUID.randomUUID().toString(),
                            app.applicationId(), command.fileName(), contentType,
                            command.content().length, command.content(), now);
                    changeStatus(connection, app.applicationId(), DRAFT, DRAFT, command.expectedVersion(), now);
                    return toApplicationView(connection,
                            repository.findApplication(connection, app.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferApplicationView deleteAttachment(String userId,
                                                          DeleteMajorTransferAttachmentCommand command) {
        String studentId = resolveStudentId(userId);
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    if (!app.studentId().equals(studentId))
                        throw error("COMMON_FORBIDDEN", "无权操作");
                    if (!MajorTransferStateMachine.studentMayEdit(app.status()))
                        throw error("TRANSFER_STATE_INVALID", "当前状态不允许编辑");
                    if (app.applicationVersion() != command.expectedVersion()) throw concurrent();
                    if (repository.deleteAttachment(connection, command.attachmentId(), app.applicationId()) != 1)
                        throw error("TRANSFER_ATTACHMENT_NOT_FOUND", "附件不存在");
                    changeStatus(connection, app.applicationId(), DRAFT, DRAFT, command.expectedVersion(), Instant.now());
                    return toApplicationView(connection,
                            repository.findApplication(connection, app.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferApplicationView submit(String userId, SubmitMajorTransferCommand command) {
        String studentId = resolveStudentId(userId);
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    if (!app.studentId().equals(studentId))
                        throw error("COMMON_FORBIDDEN", "无权操作");
                    if (!MajorTransferStateMachine.studentMaySubmit(app.status()))
                        throw error("TRANSFER_STATE_INVALID", "当前状态不允许提交");
                    if (app.reason() == null || app.reason().isBlank())
                        throw error("TRANSFER_REASON_REQUIRED", "申请理由不能为空");
                    if (app.applicationType() == MajorTransferApplicationType.DIFFICULTY) {
                        if (repository.countAttachments(connection, app.applicationId()) == 0)
                            throw error("TRANSFER_DIFFICULTY_EVIDENCE", "学困生申请必须上传至少一份证明材料");
                    }
                    MajorTransferRepository.BatchRow batch =
                            repository.findBatch(connection, app.batchId()).orElseThrow();
                    validateBatchOpen(batch, Instant.now());
                    Student student = students.findById(connection, studentId).orElseThrow(StudentNotFoundException::new);
                    validateEligibility(connection, student, batch,
                            repository.findOption(connection, app.optionId()).orElseThrow());
                    if (!student.classId().equals(app.fromClassId()))
                        throw error("TRANSFER_SOURCE_CHANGED", "原班级已变化，请重新核实申请");
                    int changed = repository.submitApplication(connection, app.applicationId(),
                            command.expectedVersion(), Instant.now());
                    if (changed == 0) throw concurrent();
                    return toApplicationView(connection,
                            repository.findApplication(connection, app.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferApplicationView withdraw(String userId, WithdrawMajorTransferCommand command) {
        String studentId = resolveStudentId(userId);
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    if (!app.studentId().equals(studentId))
                        throw error("COMMON_FORBIDDEN", "无权操作");
                    if (!MajorTransferStateMachine.studentMayWithdraw(app.status()))
                        throw error("TRANSFER_STATE_INVALID", "当前状态不允许撤回");
                    int changed = changeStatus(connection, app.applicationId(),
                            SUBMITTED, DRAFT, command.expectedVersion(), Instant.now());
                    if (changed == 0) throw concurrent();
                    try (var statement = connection.prepareStatement("UPDATE tblMajorTransferApplication SET submittedAt=NULL WHERE applicationId=?")) {
                        statement.setString(1, app.applicationId()); statement.executeUpdate();
                    }
                    changes.insertChange(connection, UUID.randomUUID().toString(), studentId,
                            "TRANSFER_WITHDRAW", app.applicationId() + ":SUBMITTED", app.applicationId() + ":DRAFT",
                            "学生撤回转专业申请", userId, LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")), Instant.now());
                    return toApplicationView(connection,
                            repository.findApplication(connection, app.applicationId()).orElseThrow());
                }));
    }

    // ── Admin: batch and option configuration ──

    @Override
    public synchronized MajorTransferBatchView saveBatch(String adminUserId, SaveMajorTransferBatchCommand command) {
        return transactions.inTransaction(connection -> {
            Instant now = Instant.now();
            if (command.status() == MajorTransferBatchStatus.OPEN && !repository.findOverlappingOpenBatches(connection,
                    command.applicationStart(), command.applicationEnd(), command.batchId()).isEmpty())
                throw error("TRANSFER_BATCH_OVERLAP", "报名时间与已有开放批次重叠");
            if (command.batchId() != null) {
                MajorTransferRepository.BatchRow existing =
                        repository.findBatch(connection, command.batchId())
                                .orElseThrow(() -> error("TRANSFER_BATCH_NOT_FOUND", "批次不存在"));
                requireChanged(repository.updateBatch(connection, command.batchId(), command.batchName(),
                        command.status(), command.applicationStart(), command.applicationEnd(),
                        command.publicityStart(), command.publicityEnd(), command.effectiveDate(),
                        command.expectedVersion(), now));
                return toBatchView(repository.findBatch(connection, command.batchId()).orElseThrow());
            } else {
                List<MajorTransferRepository.BatchRow> overlapping =
                        repository.findOverlappingOpenBatches(connection,
                                command.applicationStart(), command.applicationEnd(), null);
                if (command.status() == MajorTransferBatchStatus.OPEN && !overlapping.isEmpty())
                    throw error("TRANSFER_BATCH_OVERLAP", "报名时间与已有开放批次重叠");
                String id = UUID.randomUUID().toString();
                repository.insertBatch(connection, new MajorTransferRepository.BatchRow(
                        id, command.batchName(), command.status(),
                        command.applicationStart(), command.applicationEnd(),
                        command.publicityStart(), command.publicityEnd(), command.effectiveDate(),
                        0, now, now));
                return toBatchView(repository.findBatch(connection, id).orElseThrow());
            }
        });
    }

    @Override
    public synchronized MajorTransferOptionView saveOption(String adminUserId, SaveMajorTransferOptionCommand command) {
        return saveOption(adminUserId, command, null);
    }

    @Override
    public synchronized MajorTransferOptionView saveOption(String adminUserId,
            SaveMajorTransferOptionCommand command, String trustedDepartmentId) {
        return transactions.inTransaction(connection -> {
            Instant now = Instant.now();
            Major major = organizations.findMajor(connection, command.targetMajorId())
                    .orElseThrow(() -> error("TRANSFER_MAJOR_NOT_FOUND", "专业不存在"));
            var dept = organizations.findDepartment(connection, major.departmentId());
            if (!major.active() || dept.isEmpty() || !dept.get().active())
                throw error("TRANSFER_INVALID_TARGET", "目标学院或专业未启用");
            requireDepartment(trustedDepartmentId, major.departmentId());
            repository.findBatch(connection, command.batchId())
                    .orElseThrow(() -> error("TRANSFER_BATCH_NOT_FOUND", "批次不存在"));
            String deptName = dept.map(d -> d.departmentName()).orElse("");
            if (command.optionId() != null) {
                MajorTransferRepository.OptionRow existing =
                        repository.findOption(connection, command.optionId())
                                .orElseThrow(() -> error("TRANSFER_OPTION_NOT_FOUND", "选项不存在"));
                requireDepartment(trustedDepartmentId, existing.targetDepartmentId());
                if (existing.rowVersion() != command.expectedVersion()) throw concurrent();
                if (!existing.batchId().equals(command.batchId()) || !existing.targetMajorId().equals(command.targetMajorId()))
                    throw error("TRANSFER_INVALID_TARGET", "已有选项不能更换批次或目标专业");
                if (repository.listApplicationsByOption(connection, existing.optionId()).stream().anyMatch(a -> a.status() != DRAFT))
                    throw error("TRANSFER_OPTION_LOCKED", "已有提交申请，考核和名额规则已锁定");
                int changed = repository.updateOption(connection, new MajorTransferRepository.OptionRow(
                        command.optionId(), command.batchId(), command.targetMajorId(),
                        major.departmentId(), major.majorName(), deptName,
                        command.grades(), command.receiveQuota(), command.interviewQuota(),
                        command.writtenPassScore(), command.interviewPassScore(),
                        command.writtenWeightPct(), command.interviewWeightPct(),
                        command.difficultyQuotaExempt(), command.requirements(),
                        command.active(), existing.rowVersion(), now, now));
                if (changed == 0) throw concurrent();
                return toOptionView(repository.findOption(connection, command.optionId()).orElseThrow());
            } else {
                String id = UUID.randomUUID().toString();
                repository.insertOption(connection, new MajorTransferRepository.OptionRow(
                        id, command.batchId(), command.targetMajorId(),
                        major.departmentId(), major.majorName(), deptName,
                        command.grades(), command.receiveQuota(), command.interviewQuota(),
                        command.writtenPassScore(), command.interviewPassScore(),
                        command.writtenWeightPct(), command.interviewWeightPct(),
                        command.difficultyQuotaExempt(), command.requirements(),
                        command.active(), 0, now, now));
                return toOptionView(repository.findOption(connection, id).orElseThrow());
            }
        });
    }

    @Override
    public List<MajorTransferBatchView> listBatches() {
        return transactions.inTransaction(connection ->
                repository.listBatches(connection).stream().map(this::toBatchView).toList());
    }

    @Override
    public List<MajorTransferOptionView> listOptions(String batchId) {
        return transactions.inTransaction(connection ->
                repository.listOptionsByBatch(connection, batchId).stream()
                        .map(this::toOptionView).toList());
    }

    @Override
    public List<MajorTransferOptionView> listOptionsForCollege(
            String batchId, String trustedDepartmentId) {
        Objects.requireNonNull(trustedDepartmentId, "trustedDepartmentId");
        return transactions.inTransaction(connection ->
                repository.listOptionsByBatch(connection, batchId).stream()
                        .filter(option -> trustedDepartmentId.equals(option.targetDepartmentId()))
                        .map(this::toOptionView).toList());
    }

    // ── Admin: review workflow ──

    @Override public MajorTransferAttachmentDocument getAttachment(String attachmentId) {
        return transactions.inTransaction(c -> repository.readAttachment(c, attachmentId));
    }

    @Override
    public List<MajorTransferApplicationView> listApplications(MajorTransferApplicationQuery query) {
        return listApplications(query, null);
    }

    @Override
    public List<MajorTransferApplicationView> listApplicationsForCollege(
            MajorTransferApplicationQuery query, String departmentId) {
        Objects.requireNonNull(departmentId, "departmentId");
        return listApplications(query, departmentId);
    }

    private List<MajorTransferApplicationView> listApplications(
            MajorTransferApplicationQuery query, String departmentId) {
        return transactions.inTransaction(connection -> {
            List<MajorTransferRepository.ApplicationRow> rows;
            if (query.batchId() != null) {
                rows = departmentId == null
                        ? repository.listApplicationsByBatch(connection, query.batchId())
                        : repository.listApplicationsByBatchAndCollege(
                                connection, query.batchId(), departmentId);
            } else {
                rows = List.of();
            }
            if (query.status() != null) {
                rows = rows.stream().filter(r -> r.status() == query.status()).toList();
            }
            if (query.optionId() != null) rows = rows.stream().filter(r -> r.optionId().equals(query.optionId())).toList();
            return rows.stream().map(row -> toApplicationView(connection, row, departmentId)).toList();
        });
    }

    @Override
    public MajorTransferApplicationView getApplicationDetail(String applicationId) {
        return transactions.inTransaction(connection -> {
            MajorTransferRepository.ApplicationRow app =
                    repository.findApplication(connection, applicationId)
                            .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
            return toApplicationView(connection, app);
        });
    }

    @Override
    public MajorTransferApplicationView reviewSource(String adminUserId,
                                                      ReviewMajorTransferSourceCommand command) {
        return reviewSource(adminUserId, command, null);
    }

    @Override
    public MajorTransferApplicationView reviewSource(String adminUserId,
            ReviewMajorTransferSourceCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    requireDepartment(trustedDepartmentId, app.fromDepartmentId());
                    if (app.status() != SUBMITTED)
                        throw error("TRANSFER_STATE_INVALID", "申请状态不允许原学院审核");
                    if (command.decision() == MajorTransferDecision.APPROVE) {
                        if (!command.sourceVerified() || !command.noMisconduct() || !command.admissionAllowed())
                            throw error("TRANSFER_ATTESTATION_REQUIRED", "三项资格审核全部通过才能批准");
                        changeStatus(connection, command.applicationId(),
                                SUBMITTED, SOURCE_APPROVED, command.expectedVersion(), Instant.now());
                    } else {
                        if (command.comment() == null || command.comment().isBlank())
                            throw error("TRANSFER_REJECTION_REASON", "驳回原因不能为空");
                        changeStatus(connection, command.applicationId(),
                                SUBMITTED, REJECTED, command.expectedVersion(), Instant.now());
                    }
                    repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                            UUID.randomUUID().toString(), command.applicationId(),
                            MajorTransferReviewStage.SOURCE_REVIEW, command.decision(),
                            adminUserId, command.comment(),
                            command.sourceVerified(), command.noMisconduct(),
                            command.admissionAllowed(), Instant.now()));
                    return toApplicationView(connection,
                            repository.findApplication(connection, command.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferApplicationView reviewQualification(String adminUserId,
                                                             ReviewMajorTransferQualificationCommand command) {
        return reviewQualification(adminUserId, command, null);
    }

    @Override
    public MajorTransferApplicationView reviewQualification(String adminUserId,
            ReviewMajorTransferQualificationCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    requireTargetDepartment(connection, app, trustedDepartmentId);
                    if (app.status() != SOURCE_APPROVED)
                        throw error("TRANSFER_STATE_INVALID", "申请状态不允许转入学院资格审核");
                    if (command.decision() == MajorTransferDecision.APPROVE) {
                        changeStatus(connection, command.applicationId(),
                                SOURCE_APPROVED, QUALIFIED, command.expectedVersion(), Instant.now());
                    } else {
                        if (command.comment() == null || command.comment().isBlank())
                            throw error("TRANSFER_REJECTION_REASON", "驳回原因不能为空");
                        changeStatus(connection, command.applicationId(),
                                SOURCE_APPROVED, REJECTED, command.expectedVersion(), Instant.now());
                    }
                    repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                            UUID.randomUUID().toString(), command.applicationId(),
                            MajorTransferReviewStage.QUALIFICATION_REVIEW, command.decision(),
                            adminUserId, command.comment(),
                            null, null, null, Instant.now()));
                    return toApplicationView(connection,
                            repository.findApplication(connection, command.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferApplicationView cancel(String adminUserId, CancelMajorTransferCommand command) {
        return cancel(adminUserId, command, null);
    }

    @Override
    public MajorTransferApplicationView cancel(String adminUserId,
            CancelMajorTransferCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    requireTargetDepartment(connection, app, trustedDepartmentId);
                    if (!MajorTransferStateMachine.adminMayCancel(app.status()))
                        throw error("TRANSFER_STATE_INVALID", "当前状态不允许取消");
                    changeStatus(connection, command.applicationId(),
                            app.status(), CANCELLED, command.expectedVersion(), Instant.now());
                    repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                            UUID.randomUUID().toString(), command.applicationId(),
                            MajorTransferReviewStage.EXECUTION, MajorTransferDecision.REJECT,
                            adminUserId, command.reason(),
                            null, null, null, Instant.now()));
                    return toApplicationView(connection,
                            repository.findApplication(connection, command.applicationId()).orElseThrow());
                }));
    }

    // ── Admin: assessment and ranking ──

    @Override
    public MajorTransferApplicationView recordScore(String adminUserId,
                                                     RecordMajorTransferScoreCommand command) {
        return recordScore(adminUserId, command, null);
    }

    @Override
    public MajorTransferApplicationView recordScore(String adminUserId,
            RecordMajorTransferScoreCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    if (app.status() != QUALIFIED)
                        throw error("TRANSFER_STATE_INVALID", "申请状态不允许录入成绩");
                    MajorTransferRepository.OptionRow option =
                            repository.findOption(connection, app.optionId()).orElseThrow();
                    requireDepartment(trustedDepartmentId, option.targetDepartmentId());
                    Double written = command.writtenScore() != null
                            ? command.writtenScore().doubleValue() : null;
                    Double interview = command.interviewScore() != null
                            ? command.interviewScore().doubleValue() : null;
                    validateScore(written, option.writtenWeightPct());
                    validateScore(interview, option.interviewWeightPct());
                    Double finalScore = finalScore(written, interview,
                            option.writtenWeightPct(), option.interviewWeightPct());
                    requireChanged(repository.recordScores(connection, command.applicationId(),
                            written, interview, finalScore,
                            command.expectedVersion(), Instant.now()));
                    repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                            UUID.randomUUID().toString(), app.applicationId(), MajorTransferReviewStage.ASSESSMENT,
                            MajorTransferDecision.APPROVE, adminUserId, "考核成绩已录入，综合成绩：" + finalScore,
                            null, null, null, Instant.now()));
                    return toApplicationView(connection,
                            repository.findApplication(connection, command.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferImportResult importScores(String adminUserId,
                                                   ImportMajorTransferScoresCommand command) {
        return importScores(adminUserId, command, null);
    }

    @Override
    public MajorTransferImportResult importScores(String adminUserId,
            ImportMajorTransferScoresCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_OPTION", command.optionId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.OptionRow option =
                            repository.findOption(connection, command.optionId())
                                    .orElseThrow(() -> error("TRANSFER_OPTION_NOT_FOUND", "选项不存在"));
                    requireDepartment(trustedDepartmentId, option.targetDepartmentId());
                    int total = command.entries().size();
                    int success = 0;
                    var failures = new java.util.ArrayList<MajorTransferImportResult.Failure>();
                    for (var entry : command.entries()) {
                        try {
                            MajorTransferRepository.ApplicationRow app =
                                    repository.findApplication(connection, entry.applicationId())
                                            .or(() -> repository.findApplicationByBatchStudent(
                                                    connection, option.batchId(), entry.applicationId()))
                                            .or(() -> repository.listApplicationsByBatch(connection, option.batchId()).stream()
                                                    .filter(a -> entry.applicationId().equals(a.fromStudentNumber()))
                                                    .findFirst())
                                            .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                            if (!command.optionId().equals(app.optionId())) {
                                failures.add(new MajorTransferImportResult.Failure(
                                        entry.applicationId(), "申请不属于所选招生专业"));
                                continue;
                            }
                            if (app.status() != QUALIFIED) {
                                failures.add(new MajorTransferImportResult.Failure(
                                        entry.applicationId(), "申请状态不允许录入成绩"));
                                continue;
                            }
                            Double written = entry.writtenScore() != null
                                    ? entry.writtenScore().doubleValue() : null;
                            Double interview = entry.interviewScore() != null
                                    ? entry.interviewScore().doubleValue() : null;
                            validateScore(written, option.writtenWeightPct());
                            validateScore(interview, option.interviewWeightPct());
                            Double fs = finalScore(written, interview,
                                    option.writtenWeightPct(), option.interviewWeightPct());
                            requireChanged(repository.recordScores(connection, app.applicationId(),
                                    written, interview, fs, app.applicationVersion(), Instant.now()));
                            repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                                    UUID.randomUUID().toString(), app.applicationId(),
                                    MajorTransferReviewStage.ASSESSMENT,
                                    MajorTransferDecision.APPROVE, adminUserId,
                                    "批量导入成绩，综合成绩：" + fs, null, null, null, Instant.now()));
                            success++;
                        } catch (Exception e) {
                            failures.add(new MajorTransferImportResult.Failure(
                                    entry.applicationId(), e.getMessage()));
                        }
                    }
                    return new MajorTransferImportResult(total, success, failures.size(), failures);
                }));
    }

    @Override
    public MajorTransferScoreTemplateDocument exportScoreTemplate(
            String adminUserId, String optionId, String trustedDepartmentId) {
        return scoreTemplateExporter.exportScoreTemplate(adminUserId, optionId, trustedDepartmentId);
    }

    // ── Admin: final approval and execution ──

    /** {@inheritDoc} */
    @Override
    public MajorTransferBatchReadinessView getBatchReadiness(
            String batchId, String trustedDepartmentId) {
        return batchFinalizer.readiness(batchId, trustedDepartmentId);
    }

    /** {@inheritDoc} */
    @Override
    public MajorTransferBatchFinalizationResult finalizeBatch(String adminUserId,
            FinalizeMajorTransferBatchCommand command, String trustedDepartmentId) {
        return batchFinalizer.finalizeBatch(adminUserId, command, trustedDepartmentId);
    }

    @Override
    public MajorTransferApplicationView finalizeApproval(String adminUserId,
                                                          FinalizeMajorTransferCommand command) {
        return finalizeApproval(adminUserId, command, null);
    }

    @Override
    public MajorTransferApplicationView finalizeApproval(String adminUserId,
            FinalizeMajorTransferCommand command, String trustedDepartmentId) {
        return locks.withLocks(List.of(new ResourceKey("TRANSFER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    MajorTransferRepository.ApplicationRow app =
                            repository.findApplication(connection, command.applicationId())
                                    .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
                    requireTargetDepartment(connection, app, trustedDepartmentId);
                    if (app.status() != ASSESSED)
                        throw error("TRANSFER_STATE_INVALID", "申请状态不允许终审");
                    Instant now = Instant.now();
                    changeStatus(connection, command.applicationId(),
                            ASSESSED, PENDING_EFFECTIVE, command.expectedVersion(), now);
                    repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                            UUID.randomUUID().toString(), command.applicationId(),
                            MajorTransferReviewStage.FINAL_APPROVAL, MajorTransferDecision.APPROVE,
                            adminUserId, "终审通过",
                            null, null, null, now));
                    return toApplicationView(connection,
                            repository.findApplication(connection, command.applicationId()).orElseThrow());
                }));
    }

    @Override
    public MajorTransferApplicationView execute(String adminUserId, ExecuteMajorTransferCommand command) {
        return execute(adminUserId, command, null);
    }

    @Override
    public MajorTransferApplicationView execute(String adminUserId,
            ExecuteMajorTransferCommand command, String trustedDepartmentId) {
        String studentId = transactions.inTransaction(c -> repository.findApplication(c, command.applicationId())
                .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在")).studentId());
        List<ResourceKey> keys = List.of(
                new ResourceKey("STUDENT", studentId),
                new ResourceKey("TRANSFER_APPLICATION", command.applicationId()));
        return locks.withLocks(keys, () -> transactions.inTransaction(connection -> {
            MajorTransferRepository.ApplicationRow app =
                    repository.findApplication(connection, command.applicationId())
                            .orElseThrow(() -> error("TRANSFER_APPLICATION_NOT_FOUND", "申请不存在"));
            requireTargetDepartment(connection, app, trustedDepartmentId);
            if (app.status() != PENDING_EFFECTIVE && app.status() != EXECUTION_FAILED)
                throw error("TRANSFER_STATE_INVALID", "申请状态不允许执行");
            if (app.applicationVersion() != command.expectedVersion()) throw concurrent();
            var batch = repository.findBatch(connection, app.batchId()).orElseThrow();
            if (batch.effectiveDate() == null || Instant.now().isBefore(batch.effectiveDate()))
                throw error("TRANSFER_NOT_EFFECTIVE_YET", "尚未到生效时间或尚未配置生效日期");
            if (batch.publicityEnd() != null && Instant.now().isBefore(batch.publicityEnd()))
                throw error("TRANSFER_PUBLICITY_IN_PROGRESS", "公示尚未结束");
            Student student = students.findById(connection, app.studentId())
                    .orElseThrow(StudentNotFoundException::new);
            if (!student.classId().equals(app.fromClassId()) || student.status() != StudentStatus.ACTIVE)
                throw error("TRANSFER_SOURCE_CHANGED", "学生学籍或原班级已变化，请核实后办理");
            if (repository.listApplicationsByStudent(connection, studentId).stream()
                    .anyMatch(a -> !a.applicationId().equals(app.applicationId())
                            && (a.status() == EFFECTIVE || a.status() == PENDING_EFFECTIVE)))
                throw error("TRANSFER_ALREADY_TRANSFERRED", "已有生效的转专业记录");
            StudentClass targetClass = organizations.findClass(connection, command.targetClassId())
                    .orElseThrow(() -> error("TRANSFER_CLASS_NOT_FOUND", "目标班级不存在"));
            {
                MajorTransferRepository.OptionRow option =
                        repository.findOption(connection, app.optionId()).orElseThrow();
                if (!targetClass.majorId().equals(option.targetMajorId()))
                    throw error("TRANSFER_CLASS_MISMATCH", "目标班级不属于目标专业");
            }
            if (!targetClass.active())
                throw error("TRANSFER_CLASS_INACTIVE", "目标班级未启用");
            StudentClass sourceClass = organizations.findClass(connection, student.classId()).orElseThrow();
            int requiredTargetYear = targetCohortYear(connection, app.batchId(), sourceClass.enrollmentYear());
            if (targetClass.enrollmentYear() != requiredTargetYear) {
                throw error("TRANSFER_CLASS_YEAR_MISMATCH",
                        requiredTargetYear > sourceClass.enrollmentYear()
                                ? "大二学生转专业只允许降转至大一年级班级" : "目标班级年级不符");
            }
            Instant now = Instant.now();
            String oldDeptId = app.fromDepartmentId();
            String oldDeptName = app.fromDepartmentName();
            String oldMajorId = app.fromMajorId();
            String oldMajorName = app.fromMajorName();
            String oldClassId = app.fromClassId();
            String oldClassName = app.fromClassName();
            Major targetMajor = organizations.findMajor(connection, targetClass.majorId())
                    .orElseThrow();
            var targetDept = organizations.findDepartment(connection, targetMajor.departmentId());
            if (!targetMajor.active() || targetDept.isEmpty() || !targetDept.get().active())
                throw error("TRANSFER_INVALID_TARGET", "目标学院或专业未启用");
            String newDeptId = targetDept.map(d -> d.departmentId()).orElse("");
            String newDeptName = targetDept.map(d -> d.departmentName()).orElse("");
            String newMajorId = targetMajor.majorId();
            String newMajorName = targetMajor.majorName();
            String newClassId = targetClass.classId();
            String newClassName = targetClass.className();
            students.updateEnrollment(connection, student.studentId(), newClassId,
                    student.studentNumber(), student.rowVersion(), now);
            String oldValue = String.format("学院=%s,专业=%s,班级=%s", oldDeptName, oldMajorName, oldClassName);
            String newValue = String.format("学院=%s,专业=%s,班级=%s", newDeptName, newMajorName, newClassName);
            changes.insertChange(connection, UUID.randomUUID().toString(), student.studentId(),
                    "MAJOR_TRANSFER", oldValue, newValue, "转专业生效",
                    adminUserId, LocalDate.now(), now);
            repository.insertExecution(connection, new MajorTransferRepository.ExecutionRow(
                    UUID.randomUUID().toString(), command.applicationId(),
                    newClassId, newClassName, newMajorId, newMajorName,
                    newDeptId, newDeptName, "PENDING", adminUserId, LocalDate.now(), now));
            changeStatus(connection, command.applicationId(),
                    app.status(), EFFECTIVE, command.expectedVersion(), now);
            repository.insertReview(connection, new MajorTransferRepository.ReviewRow(
                    UUID.randomUUID().toString(), app.applicationId(), MajorTransferReviewStage.EXECUTION,
                    MajorTransferDecision.APPROVE, adminUserId, "已转入" + newMajorName + " / " + newClassName,
                    null, null, null, now));
            return toApplicationView(connection,
                    repository.findApplication(connection, command.applicationId()).orElseThrow());
        }));
    }

    // ── Helpers ──

    private void requireTargetDepartment(Connection connection,
            MajorTransferRepository.ApplicationRow application,
            String trustedDepartmentId) {
        if (trustedDepartmentId == null) return;
        MajorTransferRepository.OptionRow option = repository.findOption(
                connection, application.optionId()).orElseThrow(() ->
                error("TRANSFER_OPTION_NOT_FOUND", "选项不存在"));
        requireDepartment(trustedDepartmentId, option.targetDepartmentId());
    }

    private static void requireDepartment(String trustedDepartmentId,
            String actualDepartmentId) {
        if (trustedDepartmentId != null
                && !trustedDepartmentId.equals(actualDepartmentId)) {
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
        }
    }

    private MajorTransferWorkspace emptyWorkspace(Student student) {
        return new MajorTransferWorkspace(null, List.of(), List.of(),
                null, null, student.majorId(), null,
                student.classId(), null, student.studentNumber(), null, null);
    }

    private List<MajorTransferEligibilityItem> checkEligibility(Connection connection,
                                                                 Student student,
                                                                 MajorTransferBatchView batch,
                                                                 List<MajorTransferOptionView> options) {
        List<MajorTransferEligibilityItem> items = new ArrayList<>();
        Instant now = Instant.now();
        items.add(new MajorTransferEligibilityItem("报名时间",
                batch.status() == MajorTransferBatchStatus.OPEN && !now.isBefore(batch.applicationStart()) && !now.isAfter(batch.applicationEnd()),
                now.isBefore(batch.applicationStart()) ? "报名尚未开始" :
                        now.isAfter(batch.applicationEnd()) ? "报名已截止" : "在报名期内"));
        items.add(new MajorTransferEligibilityItem("学生类型",
                student.studentType() == StudentType.UNDERGRADUATE,
                student.studentType() == StudentType.UNDERGRADUATE ? "本科生" : "仅面向本科生"));
        boolean enrolledOnCampus = isStudentEnrolledAndOnCampus(connection, student.studentId());
        boolean active = student.status() == StudentStatus.ACTIVE;
        items.add(new MajorTransferEligibilityItem("学籍状态",
                active && enrolledOnCampus,
                !active ? "学籍状态异常" : !enrolledOnCampus ? "未在籍或未在校" : "正常"));
        if (options != null && !options.isEmpty()) {
            MajorTransferOptionView option = options.get(0);
            MajorTransferEligibilityPolicy.Result result = evaluateEligibility(connection, student,
                    batch.applicationStart(), option.targetMajorId());
            items.add(new MajorTransferEligibilityItem("年级与学院",
                    result.eligible(), result.message()));
        }
        if (repository.hasSuccessfulTransfer(connection, student.studentId())) {
            items.add(new MajorTransferEligibilityItem("转专业记录", false, "已有生效的转专业记录"));
        } else {
            items.add(new MajorTransferEligibilityItem("转专业记录", true, "无生效记录"));
        }
        return List.copyOf(items);
    }

    private static boolean isStudentEnrolledAndOnCampus(Connection c, String studentId) {
        try (var statement = c.prepareStatement("SELECT enrolled, onCampus FROM tblStudent WHERE studentId=?")) {
            statement.setString(1, studentId);
            try (var row = statement.executeQuery()) {
                return row.next() && !isFalse(row.getObject(1)) && !isFalse(row.getObject(2));
            }
        } catch (java.sql.SQLException error) {
            return false;
        }
    }

    private void validateBatchOpen(MajorTransferRepository.BatchRow batch, Instant now) {
        if (batch.status() != MajorTransferBatchStatus.OPEN)
            throw error("TRANSFER_BATCH_CLOSED", "批次未开放");
        if (now.isBefore(batch.applicationStart()) || now.isAfter(batch.applicationEnd()))
            throw error("TRANSFER_BATCH_CLOSED", "不在报名时间内");
    }

    private void validateEligibility(Connection c, Student student, MajorTransferRepository.BatchRow batch,
            MajorTransferRepository.OptionRow option) {
        if (student.studentType() != StudentType.UNDERGRADUATE || student.status() != StudentStatus.ACTIVE)
            throw error("TRANSFER_INELIGIBLE", "仅允许正常在籍的本科生申请");
        if (!isStudentEnrolledAndOnCampus(c, student.studentId()))
            throw error("TRANSFER_INELIGIBLE", "学生必须在籍且在校");
        if (!option.active() || !option.batchId().equals(batch.batchId()) || option.targetMajorId().equals(student.majorId()))
            throw error("TRANSFER_INVALID_TARGET", "目标专业不可用或与当前专业相同");
        var major = organizations.findMajor(c, option.targetMajorId()).orElseThrow();
        if (!major.active() || !organizations.findDepartment(c, major.departmentId()).orElseThrow().active())
            throw error("TRANSFER_INVALID_TARGET", "目标学院或专业未启用");
        MajorTransferEligibilityPolicy.Result result = evaluateEligibility(c, student,
                batch.applicationStart(), option.targetMajorId());
        if (!result.eligible()) throw error(result.reasonCode(), result.message());
        if (repository.hasSuccessfulTransfer(c, student.studentId()))
            throw error("TRANSFER_ALREADY_TRANSFERRED", "已有生效的转专业记录");
        if (repository.listApplicationsByStudent(c, student.studentId()).stream().anyMatch(a ->
                !a.batchId().equals(batch.batchId()) && a.status() != REJECTED && a.status() != CANCELLED && a.status() != EFFECTIVE))
            throw error("TRANSFER_DUPLICATE_APPLICATION", "已有其他批次进行中的申请");
    }

    private MajorTransferEligibilityPolicy.Result evaluateEligibility(Connection connection,
            Student student, Instant applicationStart, String targetMajorId) {
        Major currentMajor = organizations.findMajor(connection, student.majorId()).orElseThrow();
        Major targetMajor = organizations.findMajor(connection, targetMajorId).orElseThrow();
        return eligibilityPolicy.check(new MajorTransferEligibilityInput(
                student.studentType(), student.status() == StudentStatus.ACTIVE,
                isStudentEnrolledAndOnCampus(connection, student.studentId()),
                isStudentEnrolledAndOnCampus(connection, student.studentId()),
                students.findBirthDate(connection, student.studentId()).orElse(null),
                applicationStart.atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate(),
                organizations.findClass(connection, student.classId()).orElseThrow().enrollmentYear(),
                currentMajor.departmentId(), targetMajor.departmentId(), student.majorId(), targetMajorId));
    }

    private int changeStatus(Connection connection, String id, MajorTransferStatus from,
            MajorTransferStatus to, long version, Instant now) {
        int count = repository.updateApplicationStatus(connection, id, from, to, version, now);
        requireChanged(count);
        return count;
    }

    private static void requireChanged(int count) { if (count != 1) throw concurrent(); }

    private static void validateScore(Double score, int weight) {
        if ((weight > 0 && score == null) || (score != null && (!Double.isFinite(score) || score < 0 || score > 100)))
            throw error("TRANSFER_SCORE_INVALID", "有效考核项成绩必填，成绩必须在0至100之间");
    }

    private void validateAttachment(byte[] content, String declaredType, String fileName) {
        if (content.length > MAX_FILE_SIZE)
            throw error("TRANSFER_ATTACHMENT_TOO_LARGE", "附件大小不能超过5MB");
        String detected = detectContentType(content, declaredType);
        if (detected == null)
            throw error("TRANSFER_ATTACHMENT_INVALID", "仅支持PDF、JPEG、PNG格式");
    }

    private String detectContentType(byte[] content, String declaredType) {
        if (startsWith(content, PDF_MAGIC)) return "application/pdf";
        if (startsWith(content, JPEG_MAGIC)) return "image/jpeg";
        if (startsWith(content, PNG_MAGIC)) return "image/png";
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private String resolveStudentId(String userId) {
        return transactions.inTransaction(connection ->
                students.findByUserId(connection, userId)
                        .orElseThrow(StudentNotFoundException::new).studentId());
    }

    private MajorTransferApplicationView toApplicationView(Connection connection,
                                                             MajorTransferRepository.ApplicationRow row) {
        return toApplicationView(connection, row, null);
    }

    private MajorTransferApplicationView toApplicationView(Connection connection,
            MajorTransferRepository.ApplicationRow row, String managedDepartmentId) {
        List<MajorTransferReviewView> reviews = repository.listReviews(connection, row.applicationId())
                .stream().map(this::toReviewView).toList();
        List<MajorTransferApplicationView.AttachmentInfo> attachments =
                repository.listAttachments(connection, row.applicationId()).stream()
                        .map(a -> new MajorTransferApplicationView.AttachmentInfo(
                                a.attachmentId(), a.fileName(), a.contentType(), a.fileSize()))
                        .toList();
        String targetMajorId = null, targetMajorName = null, targetDeptId = null, targetDeptName = null;
        MajorTransferRepository.OptionRow option =
                repository.findOption(connection, row.optionId()).orElse(null);
        if (option != null) {
            targetMajorId = option.targetMajorId();
            targetMajorName = option.targetMajorName();
            targetDeptId = option.targetDepartmentId();
            targetDeptName = option.targetDepartmentName();
        }
        return new MajorTransferApplicationView(
                row.applicationId(), row.batchId(), row.studentId(), row.studentName(),
                row.applicationType(), row.status(), row.optionId(),
                targetMajorId, targetMajorName, targetDeptId, targetDeptName,
                row.fromDepartmentId(), row.fromDepartmentName(),
                row.fromMajorId(), row.fromMajorName(),
                row.fromClassId(), row.fromClassName(),
                row.fromStudentNumber(), row.fromGrade(),
                row.reason(), row.writtenScore(), row.interviewScore(), row.finalScore(),
                reviews, attachments,
                managedDepartmentId != null && managedDepartmentId.equals(row.fromDepartmentId()),
                managedDepartmentId != null && managedDepartmentId.equals(targetDeptId),
                row.applicationVersion(),
                row.submittedAt(), row.createdAt(), row.updatedAt());
    }

    private MajorTransferReviewView toReviewView(MajorTransferRepository.ReviewRow row) {
        return new MajorTransferReviewView(row.reviewId(), row.applicationId(),
                row.reviewStage(), row.decision(), row.reviewerUserId(), row.comment(),
                row.sourceVerified(), row.noMisconduct(), row.admissionAllowed(), row.createdAt());
    }

    private MajorTransferBatchView toBatchView(MajorTransferRepository.BatchRow row) {
        return new MajorTransferBatchView(row.batchId(), row.batchName(), row.status(),
                row.applicationStart(), row.applicationEnd(),
                row.publicityStart(), row.publicityEnd(), row.effectiveDate(), row.rowVersion());
    }

    private MajorTransferOptionView toOptionView(MajorTransferRepository.OptionRow row) {
        return new MajorTransferOptionView(row.optionId(), row.batchId(),
                row.targetMajorId(), row.targetDepartmentId(),
                row.targetMajorName(), row.targetDepartmentName(),
                row.grades(), row.receiveQuota(), row.interviewQuota(),
                row.writtenPassScore(), row.interviewPassScore(),
                row.writtenWeightPct(), row.interviewWeightPct(),
                row.difficultyQuotaExempt(), row.requirements(),
                row.active(), row.rowVersion());
    }

    static Double finalScore(Double written, Double interview, int writtenPct, int interviewPct) {
        if (written == null && writtenPct == 0) written = 0.0;
        if (interview == null && interviewPct == 0) interview = 0.0;
        if (written == null || interview == null) return null;
        BigDecimal w = BigDecimal.valueOf(written).multiply(BigDecimal.valueOf(writtenPct))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal i = BigDecimal.valueOf(interview).multiply(BigDecimal.valueOf(interviewPct))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return w.add(i).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static MajorTransferException error(String code, String message) {
        return new MajorTransferException(code, message);
    }

    private static boolean isFalse(Object value) {
        return Boolean.FALSE.equals(value) || Integer.valueOf(0).equals(value);
    }

    private int targetCohortYear(Connection c, String batchId, int sourceYear) {
        return repository.findBatch(c, batchId).map(b -> {
            LocalDate start = b.applicationStart().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate();
            int grade = start.getYear() - (start.getMonthValue() < 9 ? 1 : 0) - sourceYear + 1;
            return grade == 2 ? sourceYear + 1 : sourceYear;
        }).orElse(sourceYear == 2025 ? 2026 : sourceYear);
    }

    private static java.util.ConcurrentModificationException concurrent() {
        return new java.util.ConcurrentModificationException("数据已被修改，请刷新");
    }

}
