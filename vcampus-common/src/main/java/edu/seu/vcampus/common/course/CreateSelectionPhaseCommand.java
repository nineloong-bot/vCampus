package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/** Creates a manually controlled selection-phase draft. */
public record CreateSelectionPhaseCommand(String termId, String phaseType, String displayTitle)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public CreateSelectionPhaseCommand {
        CourseValidation.text("termId", Objects.requireNonNull(termId, "termId"), 36);
        CourseValidation.text("displayTitle", Objects.requireNonNull(displayTitle, "displayTitle"), 64);
        if (!Set.of("ENROLLMENT", "ADJUSTMENT").contains(phaseType)) {
            throw new IllegalArgumentException("invalid phase type");
        }
    }
}
