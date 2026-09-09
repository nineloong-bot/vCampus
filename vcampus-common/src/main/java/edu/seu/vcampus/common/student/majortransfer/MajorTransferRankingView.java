package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.List;

/** View of the ranked applicants for a transfer option. */
public record MajorTransferRankingView(
        String optionId,
        String targetMajorName,
        int receiveQuota,
        List<RankedApplicant> applicants,
        double cutoffScore,
        long optionVersion
) implements Serializable {

    public record RankedApplicant(
            String applicationId,
            String studentId,
            String studentName,
            String fromStudentNumber,
            MajorTransferApplicationType applicationType,
            Double writtenScore,
            Double interviewScore,
            Double finalScore,
            boolean proposed
    ) implements Serializable { }
}
