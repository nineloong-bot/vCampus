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
abstract class MajorTransferWorkflowSegment1 extends MajorTransferValidationService {
    /** Passes workflow collaborators to the shared implementation. */
    protected MajorTransferWorkflowSegment1(TransactionManager transactions, ResourceLockManager locks,
                                     MajorTransferRepository repository, StudentRepository students,
                                     StudentChangeRepository changes,
                                     AccessOrganizationRepository organizations, UserQueryPort users) {
        super(transactions, locks, repository, students, changes, organizations, users);
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
            List<MajorTransferEligibilityItem> eligibility = checkEligibility(connection, student, activeBatch);
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
}
