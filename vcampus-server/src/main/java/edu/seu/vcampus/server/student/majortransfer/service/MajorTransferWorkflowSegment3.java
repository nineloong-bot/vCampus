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
abstract class MajorTransferWorkflowSegment3 extends MajorTransferWorkflowSegment2 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment3(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
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
}
