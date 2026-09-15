package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** Command to record written and interview scores for an application. */
/**
 * Carries immutable record major transfer score command data.
 * @param applicationId the application identifier
 * @param writtenScore the written score
 * @param interviewScore the interview score
 * @param expectedVersion the expected version
 */
public record RecordMajorTransferScoreCommand(
        String applicationId,
        BigDecimal writtenScore,
        BigDecimal interviewScore,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a record major transfer score command.
     * @param applicationId the application identifier
     * @param writtenScore the written score
     * @param interviewScore the interview score
     * @param expectedVersion the expected version
     */
    public RecordMajorTransferScoreCommand {
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
