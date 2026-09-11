package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Immutable view of a major-transfer target option within a batch. */
public record MajorTransferOptionView(
        String optionId,
        String batchId,
        String targetMajorId,
        String targetDepartmentId,
        String targetMajorName,
        String targetDepartmentName,
        String grades,
        int receiveQuota,
        int interviewQuota,
        Double writtenPassScore,
        Double interviewPassScore,
        int writtenWeightPct,
        int interviewWeightPct,
        boolean difficultyQuotaExempt,
        String requirements,
        boolean active,
        long rowVersion
) implements Serializable { }
