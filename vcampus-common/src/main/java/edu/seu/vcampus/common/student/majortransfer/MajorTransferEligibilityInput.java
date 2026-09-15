package edu.seu.vcampus.common.student.majortransfer;

import edu.seu.vcampus.common.student.StudentType;

import java.io.Serializable;
import java.time.LocalDate;

/** Immutable facts required to evaluate a major-transfer application. */
public record MajorTransferEligibilityInput(
        StudentType studentType,
        boolean active,
        boolean enrolled,
        boolean onCampus,
        LocalDate birthDate,
        LocalDate applicationStart,
        int enrollmentYear,
        String currentDepartmentId,
        String targetDepartmentId,
        String currentMajorId,
        String targetMajorId
) implements Serializable { }
