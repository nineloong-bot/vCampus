package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Updates the display title of a draft selection phase. */
/**
 * Carries immutable update selection phase command data.
 * @param phaseId the phase identifier
 * @param displayTitle the display title
 * @param expectedVersion the expected version
 */
public record UpdateSelectionPhaseCommand(String phaseId, String displayTitle, long expectedVersion)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a update selection phase command.
     * @param phaseId the phase id
     * @param displayTitle the display title
     * @param expectedVersion the expected version
     */
    public UpdateSelectionPhaseCommand {
        CourseValidation.text("phaseId", Objects.requireNonNull(phaseId, "phaseId"), 36);
        CourseValidation.text("displayTitle", Objects.requireNonNull(displayTitle, "displayTitle"), 64);
        if (expectedVersion < 0) throw new IllegalArgumentException("invalid phase version");
    }
}
