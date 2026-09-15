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
abstract class MajorTransferWorkflowSegment5 extends MajorTransferWorkflowSegment4 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment5(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
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
}
