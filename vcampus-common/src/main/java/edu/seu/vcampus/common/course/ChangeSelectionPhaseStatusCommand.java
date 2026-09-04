package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/** Sets an administrator-controlled selection phase status. */
public record ChangeSelectionPhaseStatusCommand(String phaseId, String targetStatus, long expectedVersion)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public ChangeSelectionPhaseStatusCommand {
        CourseValidation.text("phaseId", Objects.requireNonNull(phaseId, "phaseId"), 36);
        if (!Set.of("DRAFT", "PREVIEW", "OPEN", "CLOSED").contains(targetStatus) || expectedVersion < 0) {
            throw new IllegalArgumentException("invalid phase status change");
        }
    }
}
