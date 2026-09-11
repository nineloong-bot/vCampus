package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to create or update a major-transfer option within a batch. */
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
