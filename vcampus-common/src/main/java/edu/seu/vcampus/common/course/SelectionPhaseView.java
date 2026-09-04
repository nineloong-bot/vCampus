package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Complete administrator-facing selection-phase state. */
public record SelectionPhaseView(String phaseId, String termId, String phaseType, String displayTitle,
                                 String phaseStatus, long rowVersion, Instant createdAt, Instant updatedAt)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public SelectionPhaseView {
        CourseValidation.text("phaseId", Objects.requireNonNull(phaseId, "phaseId"), 36);
        CourseValidation.text("termId", Objects.requireNonNull(termId, "termId"), 36);
        CourseValidation.text("displayTitle", Objects.requireNonNull(displayTitle, "displayTitle"), 64);
        if (!Set.of("ENROLLMENT", "ADJUSTMENT").contains(phaseType)
                || !Set.of("DRAFT", "PREVIEW", "OPEN", "CLOSED").contains(phaseStatus)
                || rowVersion < 0) {
            throw new IllegalArgumentException("invalid selection phase");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
