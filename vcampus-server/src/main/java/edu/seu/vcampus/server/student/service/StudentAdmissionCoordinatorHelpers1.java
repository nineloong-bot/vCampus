package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.BatchImportCommand;
import edu.seu.vcampus.common.student.BatchImportResult;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.CreateStudentManualCommand;
import edu.seu.vcampus.common.student.StudentFieldError;
import edu.seu.vcampus.common.student.StudentFieldValidator;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.StudentView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.RequestContext;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.numbering.CampusCardNumberGenerator;
import edu.seu.vcampus.server.student.numbering.StudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.user.service.UserAccountProvisioningPort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Provides focused helper operations for {@link StudentAdmissionCoordinator}. */
abstract class StudentAdmissionCoordinatorHelpers1 extends StudentAdmissionCoordinatorHelpers2 {
    protected StudentAdmissionCoordinatorHelpers1(TransactionManager transactions, ResourceLockManager locks,
            RequestDeduplicator deduplicator, OrganizationRepository organizations,
            CampusCardNumberGenerator campusCards, StudentNumberGenerator studentNumbers,
            UserAccountProvisioningPort accounts, StudentRepository students,
            StudentChangeRepository changes) {
        super(transactions, locks, deduplicator, organizations, campusCards, studentNumbers, accounts, students, changes);
    }


    protected BatchImportResult batchImportInTransaction(TransactionContext tx,
            BatchImportCommand command, RequestContext request,
            String trustedDepartmentId) throws Exception {
        Major major = organizations.findMajor(tx.connection(), command.majorId())
                .orElseThrow(() -> new StudentAdmissionException(
                        "STUDENT_ORGANIZATION_MISMATCH", "专业不存在"));
        if (!major.active())
            throw new StudentAdmissionException("STUDENT_CLASS_INACTIVE", "专业已停用");
        requireDepartment(major, trustedDepartmentId);
        List<StudentClass> classes = new ArrayList<>();
        for (String classId : command.classIds()) {
            StudentClass sc = organizations.findClass(tx.connection(), classId)
                    .orElseThrow(() -> new StudentAdmissionException(
                            "STUDENT_ORGANIZATION_MISMATCH", "班级不存在: " + classId));
            if (!sc.active())
                throw new StudentAdmissionException("STUDENT_CLASS_INACTIVE", "班级已停用: " + sc.className());
            if (!sc.majorId().equals(major.majorId()))
                throw new StudentAdmissionException("STUDENT_ORGANIZATION_MISMATCH",
                        "班级 " + sc.className() + " 不属于所选专业");
            classes.add(sc);
        }
        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        int created = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < command.entries().size(); i++) {
            var entry = command.entries().get(i);
            String campusCard = entry.campusCardNumber();
            if (campusCard == null || campusCard.isBlank()) {
                errors.add("第 " + (i + 1) + " 条: 一卡通号为空");
                continue;
            }
            try {
                StudentClass targetClass = classes.get(entry.classIndex());
                String studentNumber = studentNumbers.next(tx, major.majorCode(),
                        targetClass.enrollmentYear(), targetClass.classNumber());
                var account = accounts.createStudentAccount(tx, campusCard,
                        "12345678".toCharArray());
                Student student = new Student(UUID.randomUUID().toString(), account.userId(),
                        studentNumber, StudentType.UNDERGRADUATE, entry.studentName(), entry.gender(),
                        null, null, major.majorId(), targetClass.classId(),
                        today, StudentStatus.ACTIVE, 0, now, now);
                students.insert(tx.connection(), student);
                changes.insertChange(tx.connection(), UUID.randomUUID().toString(),
                        student.studentId(), "BATCH_IMPORT", null,
                        "studentNumber=" + studentNumber + ";classId=" + targetClass.classId(),
                        "批量导入学生", request.userId(), today, now);
                created++;
            } catch (Exception e) {
                errors.add("第 " + (i + 1) + " 条 (" + entry.studentName() + "): " + e.getMessage());
            }
        }
        return new BatchImportResult(created, errors.size(), errors);
    }
}
