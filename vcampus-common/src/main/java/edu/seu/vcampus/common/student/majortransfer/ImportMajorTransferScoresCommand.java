package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Command to bulk-import exam/interview scores for a batch option. */
public record ImportMajorTransferScoresCommand(
        String optionId,
        List<ScoreEntry> entries,
        long expectedVersion
) implements Serializable {
    public ImportMajorTransferScoresCommand {
        Objects.requireNonNull(optionId, "optionId");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /** One student's scores in a bulk import. */
    public record ScoreEntry(
            String applicationId,
            BigDecimal writtenScore,
            BigDecimal interviewScore
    ) implements Serializable {
        public ScoreEntry {
            Objects.requireNonNull(applicationId, "applicationId");
        }
    }
}
