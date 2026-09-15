package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.List;

/** Student workspace: active batch, options, eligibility, formal snapshot, and application. */
/**
 * Carries immutable major transfer workspace data.
 * @param activeBatch the active batch
 * @param availableOptions the available options
 * @param eligibilityItems the eligibility items
 * @param currentDepartmentId the current department identifier
 * @param currentDepartmentName the current department name
 * @param currentMajorId the current major identifier
 * @param currentMajorName the current major name
 * @param currentClassId the current class identifier
 * @param currentClassName the current class name
 * @param currentStudentNumber the current student number
 * @param currentGrade the current grade
 * @param application the application
 */
public record MajorTransferWorkspace(
        MajorTransferBatchView activeBatch,
        List<MajorTransferOptionView> availableOptions,
        List<MajorTransferEligibilityItem> eligibilityItems,
        String currentDepartmentId,
        String currentDepartmentName,
        String currentMajorId,
        String currentMajorName,
        String currentClassId,
        String currentClassName,
        String currentStudentNumber,
        String currentGrade,
        MajorTransferApplicationView application
) implements Serializable { }
