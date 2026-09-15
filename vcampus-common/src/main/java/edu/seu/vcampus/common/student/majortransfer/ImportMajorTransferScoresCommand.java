package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Command to bulk-import exam/interview scores for a batch option. */
/**
 * Carries immutable import major transfer scores command data.
 * @param optionId the option identifier
 * @param entries the entries
 * @param expectedVersion the expected version
 */
public record ImportMajorTransferScoresCommand(
        String optionId,
        List<ScoreEntry> entries,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a import major transfer scores command.
     * @param optionId the option identifier
     * @param entries the entries
     * @param expectedVersion the expected version
     */
    public ImportMajorTransferScoresCommand {
        Objects.requireNonNull(optionId, "optionId");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /** One student's scores in a bulk import. */
    /**
 * Carries immutable score entry data.
 * @param applicationId the application identifier
 * @param writtenScore the written score
 * @param interviewScore the interview score
 */
public record ScoreEntry(
            String applicationId,
            BigDecimal writtenScore,
            BigDecimal interviewScore
    ) implements Serializable {
        /**
         * Validates and creates a score entry.
         * @param applicationId the application identifier
         * @param writtenScore the written score
         * @param interviewScore the interview score
         */
        public ScoreEntry {
            Objects.requireNonNull(applicationId, "applicationId");
        }
    }
}
