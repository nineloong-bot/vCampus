package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to create or update a major-transfer option within a batch. */
/**
 * Carries immutable save major transfer option command data.
 * @param optionId the option identifier
 * @param batchId the batch identifier
 * @param targetMajorId the target major identifier
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
 * @param expectedVersion the expected version
 */
public record SaveMajorTransferOptionCommand(
        String optionId,
        String batchId,
        String targetMajorId,
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
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a save major transfer option command.
     * @param optionId the option id
     * @param batchId the batch id
     * @param targetMajorId the target major id
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
     * @param expectedVersion the expected version
     */
    public SaveMajorTransferOptionCommand {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(targetMajorId, "targetMajorId");
        Objects.requireNonNull(grades, "grades");
        if (!grades.matches("[0-9]{4}(\\s*,\\s*[0-9]{4})*")) throw new IllegalArgumentException("年级填写入学年份，如2024,2025");
        if (writtenWeightPct < 0 || interviewWeightPct < 0 || writtenWeightPct > 100 || interviewWeightPct > 100)
            throw new IllegalArgumentException("考核权重必须在0至100之间");
        if ((writtenPassScore != null && (!Double.isFinite(writtenPassScore) || writtenPassScore < 0 || writtenPassScore > 100))
                || (interviewPassScore != null && (!Double.isFinite(interviewPassScore) || interviewPassScore < 0 || interviewPassScore > 100)))
            throw new IllegalArgumentException("合格线必须在0至100之间");
        if (receiveQuota < 0) throw new IllegalArgumentException("接收名额不能为负数");
        if (interviewQuota < 0) throw new IllegalArgumentException("面试名额不能为负数");
        if (writtenWeightPct + interviewWeightPct != 100) {
            throw new IllegalArgumentException("笔试和面试权重之和必须为100");
        }
    }
}
