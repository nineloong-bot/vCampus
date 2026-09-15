package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Immutable view of a major-transfer target option within a batch. */
/**
 * Carries immutable major transfer option view data.
 * @param optionId the option identifier
 * @param batchId the batch identifier
 * @param targetMajorId the target major identifier
 * @param targetDepartmentId the target department identifier
 * @param targetMajorName the target major name
 * @param targetDepartmentName the target department name
 * @param grades the grades
 * @param receiveQuota the receive quota
 * @param interviewQuota the interview quota
 * @param writtenPassScore the written pass score
 * @param interviewPassScore the interview pass score
 * @param writtenWeightPct the written weight pct
 * @param interviewWeightPct the interview weight pct
 * @param difficultyQuotaExempt the difficulty quota exempt
 * @param requirements the requirements
 * @param active the active
 * @param rowVersion the row version
 */
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
