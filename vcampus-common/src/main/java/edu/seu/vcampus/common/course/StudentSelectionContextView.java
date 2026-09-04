package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Student-facing snapshot of the active term, open phase, and enrollment eligibility. */
public record StudentSelectionContextView(String termId, String termName, String termStatus,
                                          String phaseId, String phaseType, String displayTitle,
                                          Instant serverTime, boolean studentEligible,
                                          String ineligibleReason) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public StudentSelectionContextView {
        CourseValidation.text("termId", Objects.requireNonNull(termId, "termId"), 36);
        CourseValidation.text("termName", Objects.requireNonNull(termName, "termName"), 64);
        if (!Set.of("PLANNED", "ACTIVE", "CLOSED").contains(termStatus)) {
            throw new IllegalArgumentException("invalid term status");
        }
        Objects.requireNonNull(serverTime, "serverTime");
        boolean noPhase = phaseId == null && phaseType == null && displayTitle == null;
        boolean completePhase = phaseId != null && phaseType != null && displayTitle != null;
        if (!noPhase && !completePhase) throw new IllegalArgumentException("incomplete phase context");
        if (completePhase) {
            CourseValidation.text("phaseId", phaseId, 36);
            CourseValidation.text("displayTitle", displayTitle, 64);
            if (!Set.of("ENROLLMENT", "ADJUSTMENT").contains(phaseType)) {
                throw new IllegalArgumentException("invalid phase type");
            }
        }
        CourseValidation.optionalText("ineligibleReason", ineligibleReason, 128);
    }
}
