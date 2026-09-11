package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** Command to record written and interview scores for an application. */
public record RecordMajorTransferScoreCommand(
        String applicationId,
        BigDecimal writtenScore,
        BigDecimal interviewScore,
        long expectedVersion
) implements Serializable {
    public RecordMajorTransferScoreCommand {
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
