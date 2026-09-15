package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.StudentView;

import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;

/** Status/type label and parsing helpers for the admin edit dialog. */
abstract class AdminStudentInfoEditDialogLabels extends AdminStudentInfoEditDialogBase {

    /** Creates the label-helper segment of the admin edit dialog. */
    protected AdminStudentInfoEditDialogLabels(Window owner, StudentClientService students,
            StudentView initial, StudentAcademicProfile academic, Consumer<StudentView> saved) {
        super(owner, students, initial, academic, saved);
    }

    static String statusLabel(StudentStatus status) {
        if (status == null) return "正常";
        return switch (status) {
            case ACTIVE -> "正常";
            case SUSPENDED -> "休学";
            case GRADUATED -> "已毕业";
            case WITHDRAWN -> "已退学";
        };
    }

    static StudentStatus parseStatus(String label) {
        if (label == null) return null;
        return switch (label) {
            case "正常" -> StudentStatus.ACTIVE;
            case "休学" -> StudentStatus.SUSPENDED;
            case "已毕业" -> StudentStatus.GRADUATED;
            case "已退学" -> StudentStatus.WITHDRAWN;
            default -> null;
        };
    }

    static String studentTypeLabel(StudentType type) {
        if (type == null) return "本科生";
        return switch (type) {
            case UNDERGRADUATE -> "本科生";
            case MASTER -> "硕士生";
            case DOCTORATE -> "博士生";
        };
    }

    static StudentType parseStudentType(String label) {
        if (label == null) return null;
        return switch (label) {
            case "本科生" -> StudentType.UNDERGRADUATE;
            case "硕士生" -> StudentType.MASTER;
            case "博士生" -> StudentType.DOCTORATE;
            default -> null;
        };
    }

    static Boolean parseYesNo(String label) {
        return label == null ? null : "是".equals(label);
    }

    static Integer parseProgramLength(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) return null;
        try { return Integer.parseInt(trimmed); }
        catch (NumberFormatException failure) { return null; }
    }

    static LocalDate parseDate(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) return null;
        try { return LocalDate.parse(trimmed); }
        catch (DateTimeParseException failure) { return null; }
    }
}
