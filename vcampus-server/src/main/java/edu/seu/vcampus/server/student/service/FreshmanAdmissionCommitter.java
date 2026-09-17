package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.FreshmanAdmissionCommand;
import edu.seu.vcampus.common.student.FreshmanAdmissionResult;
import edu.seu.vcampus.common.student.FreshmanAdmissionStudentResult;
import edu.seu.vcampus.common.student.FreshmanClassAssignment;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.persistence.TransactionContext;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Commits a preplanned freshman batch in one caller-owned Access transaction. */
final class FreshmanAdmissionCommitter {
    private final OrganizationRepository organizations;
    private final CampusCardNumberGenerator campusCards;
    private final StudentNumberGenerator studentNumbers;
    private final UserAccountProvisioningPort accounts;
    private final StudentRepository students;
    private final StudentChangeRepository changes;
    private final RequestDeduplicator deduplicator;
    private final AutumnTermCalendarPort calendar;

    FreshmanAdmissionCommitter(OrganizationRepository organizations, CampusCardNumberGenerator campusCards,
            StudentNumberGenerator studentNumbers, UserAccountProvisioningPort accounts,
            StudentRepository students, StudentChangeRepository changes, RequestDeduplicator deduplicator,
            AutumnTermCalendarPort calendar) {
        this.organizations = organizations; this.campusCards = campusCards; this.studentNumbers = studentNumbers;
        this.accounts = accounts; this.students = students; this.changes = changes; this.deduplicator = deduplicator;
        this.calendar = calendar;
    }

    FreshmanAdmissionResult admit(TransactionContext tx, FreshmanAdmissionCommand command,
            RequestContext request, String trustedDepartmentId) throws Exception {
        var replay = deduplicator.replayCompleted(tx, request.requestId());
        if (replay.isPresent()) return replay(replay.get());
        FreshmanAdmissionPlan plan = new FreshmanAdmissionPlanner(organizations, students, calendar)
                .plan(tx.connection(), command, trustedDepartmentId);
        Map<String, StudentClass> classes = createClasses(tx, plan, command.enrollmentYear());
        Instant now = Instant.now();
        LocalDate date = LocalDate.of(command.enrollmentYear(), 9, 1);
        List<FreshmanAdmissionStudentResult> results = new java.util.ArrayList<>();
        temporarilyReleaseStudentNumbers(tx, plan, now);
        resetStudentNumbers(tx, plan, command.enrollmentYear());
        for (FreshmanClassAssignment assignment : plan.preview().assignments()) {
            Major major = plan.majorsByLine().get(assignment.row().lineNumber());
            StudentClass studentClass = classes.get(key(major.majorId(), assignment.classNumber()));
            Student existing = plan.existingByLine().get(assignment.row().lineNumber());
            String number = studentNumbers.next(tx, major.majorCode(), command.enrollmentYear(),
                    assignment.classNumber());
            if (existing != null) {
                students.updateEnrollment(tx.connection(), existing.studentId(), studentClass.classId(), number,
                        existing.rowVersion() + 1, now);
                changes.insertChange(tx.connection(), UUID.randomUUID().toString(), existing.studentId(),
                        "FRESHMAN_REBALANCE", null, "studentNumber=" + number + ";classId=" + studentClass.classId(),
                        "新生分批录取重分班", request.userId(), date, now);
                continue;
            }
            if (students.existsByIdDocumentNumber(tx.connection(), assignment.row().idDocumentNumber()))
                throw new StudentAdmissionException("STUDENT_ID_DOCUMENT_DUPLICATE",
                        "第 " + assignment.row().lineNumber() + " 行身份证已录取");
            String card = campusCards.next(tx, StudentType.UNDERGRADUATE, command.enrollmentYear());
            var account = accounts.createStudentAccount(tx, card, "12345678".toCharArray());
            Student student = new Student(UUID.randomUUID().toString(), account.userId(), number,
                    StudentType.UNDERGRADUATE, assignment.row().name(), assignment.row().gender(), null, null,
                    major.majorId(), studentClass.classId(), date, StudentStatus.ACTIVE, 0, now, now);
            students.insertManual(tx.connection(), student, "居民身份证", assignment.row().idDocumentNumber(), birthDate(assignment.row().idDocumentNumber()));
            changes.insertChange(tx.connection(), UUID.randomUUID().toString(), student.studentId(), "ADMISSION",
                    null, "studentNumber=" + number + ";classId=" + studentClass.classId(), "新生批量录取",
                    request.userId(), date, now);
            results.add(new FreshmanAdmissionStudentResult(assignment.row().lineNumber(), number, assignment.className()));
        }
        FreshmanAdmissionResult result = new FreshmanAdmissionResult(results.size(), results);
        deduplicator.storeCompleted(tx, new Message(request.requestId(), MessageType.REQUEST,
                "STUDENT_FRESHMAN_ADMIT", null, command, System.currentTimeMillis()), ResponseBody.success(result));
        return result;
    }

    private void temporarilyReleaseStudentNumbers(TransactionContext tx, FreshmanAdmissionPlan plan,
            Instant now) {
        int temporaryNumber = 1;
        for (Student student : plan.existingByLine().values()) {
            students.updateEnrollment(tx.connection(), student.studentId(), student.classId(),
                    "T" + String.format("%07d", temporaryNumber++),
                    student.rowVersion(), now);
        }
    }

    private void resetStudentNumbers(TransactionContext tx, FreshmanAdmissionPlan plan, int year) {
        Set<String> reset = new HashSet<>();
        for (FreshmanClassAssignment assignment : plan.preview().assignments()) {
            Major major = plan.majorsByLine().get(assignment.row().lineNumber());
            String key = key(major.majorId(), assignment.classNumber());
            if (reset.add(key)) studentNumbers.reset(tx, major.majorCode(), year, assignment.classNumber());
        }
    }

    private Map<String, StudentClass> createClasses(TransactionContext tx, FreshmanAdmissionPlan plan, int year) {
        Map<String, StudentClass> result = new HashMap<>();
        for (FreshmanClassAssignment assignment : plan.preview().assignments()) {
            Major major = plan.majorsByLine().get(assignment.row().lineNumber());
            String key = key(major.majorId(), assignment.classNumber());
            if (result.containsKey(key)) continue;
            StudentClass studentClass = organizations.listClasses(tx.connection(), major.majorId(), false).stream()
                    .filter(value -> value.enrollmentYear() == year
                            && value.classNumber() == assignment.classNumber()).findFirst().orElse(null);
            if (studentClass == null) {
                studentClass = new StudentClass(UUID.randomUUID().toString(), major.majorId(),
                        major.majorCode() + "-" + year + "-" + String.format("%02d", assignment.classNumber()),
                        assignment.className(), year, assignment.classNumber(), true, 0);
                organizations.insertClass(tx.connection(), studentClass);
            }
            result.put(key, studentClass);
        }
        return result;
    }

    private static LocalDate birthDate(String id) {
        return LocalDate.parse(id.substring(6, 14), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static String key(String majorId, int classNumber) { return majorId + "\u0000" + classNumber; }

    private static FreshmanAdmissionResult replay(ResponseBody<?> body) {
        if (body.success() && body.data() instanceof FreshmanAdmissionResult result) return result;
        throw new StudentAdmissionException(body.code(), body.message());
    }
}
