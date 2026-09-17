package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.FreshmanAdmissionCommand;
import edu.seu.vcampus.common.student.FreshmanAdmissionCsv;
import edu.seu.vcampus.common.student.FreshmanAdmissionPreview;
import edu.seu.vcampus.common.student.FreshmanAdmissionRow;
import edu.seu.vcampus.common.student.FreshmanClassAssigner;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves a validated freshman CSV against the live organization hierarchy. */
final class FreshmanAdmissionPlanner {
    private static final int EXISTING_LINE_BASE = 1_000_000;
    private final OrganizationRepository organizations;
    private final StudentRepository students;
    private final AutumnTermCalendarPort calendar;

    FreshmanAdmissionPlanner(OrganizationRepository organizations, StudentRepository students,
            AutumnTermCalendarPort calendar) {
        this.organizations = Objects.requireNonNull(organizations);
        this.students = Objects.requireNonNull(students);
        this.calendar = Objects.requireNonNull(calendar);
    }

    FreshmanAdmissionPreview preview(Connection connection, FreshmanAdmissionCommand command,
                                     String trustedDepartmentId) {
        return plan(connection, command, trustedDepartmentId).preview();
    }

    FreshmanAdmissionPlan plan(Connection connection, FreshmanAdmissionCommand command,
                               String trustedDepartmentId) {
        requireAdmissionOpen(connection, command.enrollmentYear());
        var parsed = FreshmanAdmissionCsv.parse(command.csv());
        if (!parsed.errors().isEmpty()) {
            var error = parsed.errors().getFirst();
            throw new StudentAdmissionException("STUDENT_FRESHMAN_CSV_INVALID",
                    "第 " + error.lineNumber() + " 行 " + error.field() + "：" + error.message());
        }
        Map<String, Department> departments = new HashMap<>();
        Map<String, Major> majors = new HashMap<>();
        Map<Integer, Major> majorsByLine = new HashMap<>();
        List<FreshmanAdmissionRow> cohort = new ArrayList<>(parsed.rows());
        for (var row : parsed.rows()) {
            Department department = departments.computeIfAbsent(row.departmentName(),
                    name -> department(connection, name));
            if (!department.active()) throw new StudentAdmissionException(
                    "STUDENT_CLASS_INACTIVE", "学院已停用：" + department.departmentName());
            if (trustedDepartmentId != null && !trustedDepartmentId.equals(department.departmentId()))
                throw new IllegalArgumentException("COMMON_FORBIDDEN");
            Major major = majors.computeIfAbsent(department.departmentId() + "\u0000" + row.majorName(),
                    ignored -> major(connection, department, row.majorName()));
            if (!major.active()) throw new StudentAdmissionException(
                    "STUDENT_CLASS_INACTIVE", "专业已停用：" + major.majorName());
            if (students.existsByIdDocumentNumber(connection, row.idDocumentNumber()))
                throw new StudentAdmissionException("STUDENT_ID_DOCUMENT_DUPLICATE",
                        "第 " + row.lineNumber() + " 行身份证已录取");
            majorsByLine.put(row.lineNumber(), major);
        }
        Map<Integer, Student> existingByLine = new HashMap<>();
        int existingLine = EXISTING_LINE_BASE;
        for (Major major : majors.values()) {
            Department department = departments.values().stream()
                    .filter(value -> value.departmentId().equals(major.departmentId())).findFirst().orElseThrow();
            for (Student student : students.findFreshmen(connection, major.majorId(), command.enrollmentYear())) {
                FreshmanAdmissionRow row = new FreshmanAdmissionRow(existingLine++, student.studentName(),
                        student.gender(), "~" + student.studentId(), department.departmentName(), major.majorName());
                cohort.add(row); majorsByLine.put(row.lineNumber(), major);
                existingByLine.put(row.lineNumber(), student);
            }
        }
        return new FreshmanAdmissionPlan(new FreshmanAdmissionPreview(command.enrollmentYear(),
                FreshmanClassAssigner.assign(cohort, command.enrollmentYear())), majorsByLine, existingByLine);
    }

    private void requireAdmissionOpen(Connection connection, int year) {
        LocalDate start = calendar.findAutumnStartDate(connection, year).orElseThrow(() ->
                new StudentAdmissionException("STUDENT_FRESHMAN_TERM_MISSING", "未配置该入学年份秋季学期"));
        if (!LocalDate.now().isBefore(start)) throw new StudentAdmissionException(
                "STUDENT_FRESHMAN_ADMISSION_CLOSED", "秋季学期开课后不允许录取新生");
    }

    private Department department(Connection connection, String name) {
        return organizations.listDepartments(connection, true).stream()
                .filter(value -> value.departmentName().equals(name)).findFirst().orElseThrow(() ->
                        new StudentAdmissionException("STUDENT_ORGANIZATION_MISMATCH", "学院不存在：" + name));
    }

    private Major major(Connection connection, Department department, String name) {
        return organizations.listActiveMajors(connection, department.departmentId()).stream()
                .filter(value -> value.majorName().equals(name)).findFirst().orElseThrow(() -> {
                    boolean existsElsewhere = organizations.listDepartments(connection, true).stream()
                            .flatMap(value -> organizations.listActiveMajors(connection, value.departmentId()).stream())
                            .anyMatch(value -> value.majorName().equals(name));
                    return new StudentAdmissionException("STUDENT_ORGANIZATION_MISMATCH", existsElsewhere
                            ? "专业不属于学院：" + name : "专业不存在：" + name);
                });
    }
}

/** Resolved organization metadata associated with a safe freshman preview. */
record FreshmanAdmissionPlan(FreshmanAdmissionPreview preview, Map<Integer, Major> majorsByLine,
        Map<Integer, Student> existingByLine) {
    FreshmanAdmissionPlan {
        majorsByLine = Map.copyOf(majorsByLine);
        existingByLine = Map.copyOf(existingByLine);
    }
}
