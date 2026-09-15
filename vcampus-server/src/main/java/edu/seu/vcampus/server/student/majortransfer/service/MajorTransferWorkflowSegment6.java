package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
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

/** Implements a focused segment of the major-transfer workflow. */
abstract class MajorTransferWorkflowSegment6 extends MajorTransferWorkflowSegment5 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment6(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
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
}
