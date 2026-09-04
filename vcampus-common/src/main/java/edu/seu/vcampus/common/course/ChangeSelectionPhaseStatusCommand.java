package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/** Opens a draft selection phase or closes the current open phase. */
public record ChangeSelectionPhaseStatusCommand(String phaseId, String targetStatus, long expectedVersion)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public ChangeSelectionPhaseStatusCommand {
        CourseValidation.text("phaseId", Objects.requireNonNull(phaseId, "phaseId"), 36);
        if (!Set.of("OPEN", "CLOSED").contains(targetStatus) || expectedVersion < 0) {
            throw new IllegalArgumentException("invalid phase status change");
        }
    }
}
