package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.List;

/** Student workspace: active batch, options, eligibility, formal snapshot, and application. */
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
