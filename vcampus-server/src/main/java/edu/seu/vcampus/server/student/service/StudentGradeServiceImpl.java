package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.StudentGrade;
import edu.seu.vcampus.server.student.domain.TrainingPlan;
import edu.seu.vcampus.server.student.domain.TrainingPlanCourse;
import edu.seu.vcampus.server.student.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Transactional student grade service. */
public final class StudentGradeServiceImpl implements StudentGradeService {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final StudentGradeRepository grades;
    private final TrainingPlanRepository plans;
    private final StudentRepository students;

    public StudentGradeServiceImpl(TransactionManager transactions, ResourceLockManager locks,
            StudentGradeRepository grades, TrainingPlanRepository plans,
            StudentRepository students) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.grades = Objects.requireNonNull(grades);
        this.plans = Objects.requireNonNull(plans);
        this.students = Objects.requireNonNull(students);
    }

    @Override
    public StudentGradeView recordGrade(RecordStudentGradeCommand command, String operatorUserId) {
        Objects.requireNonNull(command.studentId());
        Objects.requireNonNull(command.planCourseId());
        Objects.requireNonNull(command.result());
        return locks.withLocks(List.of(
                        new ResourceKey("STUDENT", command.studentId()),
                        new ResourceKey("TRAINING_PLAN_COURSE", command.planCourseId())),
                () -> transactions.inTransaction(connection -> {
            students.findById(connection, command.studentId())
                    .orElseThrow(StudentNotFoundException::new);
            TrainingPlanCourse course = plans.findCourseById(connection, command.planCourseId())
                    .orElseThrow(() -> new TrainingPlanException("TRAINING_PLAN_COURSE_NOT_FOUND",
                            "课程不存在"));
            Instant now = Instant.now();
            var existing = grades.findByStudentAndCourse(connection, command.studentId(),
                    command.planCourseId());
            if (existing.isPresent()) {
                StudentGrade updated = new StudentGrade(existing.get().gradeId(),
                        command.studentId(), command.planCourseId(), command.result(),
                        command.recordedSemester(), operatorUserId,
                        existing.get().rowVersion(), existing.get().createdAt(), now);
                grades.update(connection, updated, existing.get().rowVersion());
                return gradeView(updated, course);
            } else {
                String gradeId = UUID.randomUUID().toString();
                StudentGrade grade = new StudentGrade(gradeId, command.studentId(),
                        command.planCourseId(), command.result(), command.recordedSemester(),
                        operatorUserId, 0, now, now);
                grades.insert(connection, grade);
                return gradeView(grade, course);
            }
        }));
    }

    @Override
    public List<StudentGradeView> batchRecordGrades(BatchRecordGradesCommand command,
            String operatorUserId) {
        Objects.requireNonNull(command.entries());
        return command.entries().stream()
                .map(entry -> recordGrade(new RecordStudentGradeCommand(entry.studentId(),
                        entry.planCourseId(), entry.result(), entry.recordedSemester()),
                        operatorUserId))
                .toList();
    }

    @Override
    public StudentTranscriptView getTranscriptByStudentId(String studentId) {
        return transactions.inTransaction(connection -> {
            Student student = students.findById(connection, studentId)
                    .orElseThrow(StudentNotFoundException::new);
            return buildTranscript(connection, student);
        });
    }

    @Override
    public StudentTranscriptView getMyTranscript(String userId) {
        return transactions.inTransaction(connection -> {
            Student student = students.findByUserId(connection, userId)
                    .orElseThrow(StudentNotFoundException::new);
            return buildTranscript(connection, student);
        });
    }

    private StudentTranscriptView buildTranscript(java.sql.Connection connection,
            Student student) {
        List<StudentGradeView> gradeViews = grades.listByStudent(connection, student.studentId());
        int requiredPassed = 0, requiredTotal = 0, electivePassed = 0, electiveTotal = 0;
        BigDecimal creditsEarned = BigDecimal.ZERO;
        for (StudentGradeView g : gradeViews) {
            if (g.courseType() == CourseType.REQUIRED) {
                requiredTotal++;
                if (g.result() == GradeResult.PASSED) {
                    requiredPassed++;
                    creditsEarned = creditsEarned.add(g.credits());
                }
            } else {
                electiveTotal++;
                if (g.result() == GradeResult.PASSED) {
                    electivePassed++;
                    creditsEarned = creditsEarned.add(g.credits());
                }
            }
        }
        var plan = plans.findByMajorAndYear(connection, student.majorId(),
                Integer.parseInt("20" + student.studentNumber().substring(3, 5)));
        long minElectiveCount = plan.map(TrainingPlan::minElectiveCount).orElse(0L);
        BigDecimal minElectiveCredits = plan.map(TrainingPlan::minElectiveCredits)
                .orElse(BigDecimal.ZERO);
        var major = new AccessOrganizationRepository().findMajor(connection, student.majorId());
        String majorName = major.map(m -> m.majorName()).orElse(null);
        return new StudentTranscriptView(student.studentId(), student.studentName(),
                student.studentNumber(), majorName,
                Integer.parseInt("20" + student.studentNumber().substring(3, 5)),
                minElectiveCount, minElectiveCredits, gradeViews,
                requiredPassed, requiredTotal, electivePassed, electiveTotal, creditsEarned);
    }

    private StudentGradeView gradeView(StudentGrade grade, TrainingPlanCourse course) {
        return new StudentGradeView(grade.gradeId(), grade.studentId(), grade.planCourseId(),
                course.courseCode(), course.courseName(), course.credits(), course.courseType(),
                course.semester(), grade.result(), grade.recordedSemester(), grade.rowVersion());
    }
}
