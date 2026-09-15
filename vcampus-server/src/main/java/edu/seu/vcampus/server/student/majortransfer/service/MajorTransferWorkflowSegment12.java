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
abstract class MajorTransferWorkflowSegment12 extends MajorTransferWorkflowSegment11 {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment12(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
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
}
