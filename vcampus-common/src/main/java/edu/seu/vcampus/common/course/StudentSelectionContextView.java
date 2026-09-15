package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Student-facing snapshot of the active term, open phase, and enrollment eligibility. */
/**
 * Carries immutable student selection context view data.
 * @param termId the term identifier
 * @param termName the term name
 * @param termStatus the term status
 * @param phaseId the phase identifier
 * @param phaseType the phase type
 * @param displayTitle the display title
 * @param phaseStatus the phase status
 * @param serverTime the server time
 * @param studentEligible the student eligible
 * @param ineligibleReason the ineligible reason
 */
public record StudentSelectionContextView(String termId, String termName, String termStatus,
                                          String phaseId, String phaseType, String displayTitle,
                                          String phaseStatus,
                                          Instant serverTime, boolean studentEligible,
                                          String ineligibleReason) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a student selection context view.
     * @param termId the term id
     * @param termName the term name
     * @param termStatus the term status
     * @param phaseId the phase id
     * @param phaseType the phase type
     * @param displayTitle the display title
     * @param phaseStatus the phase status
     * @param serverTime the server time
     * @param studentEligible the student eligible
     * @param ineligibleReason the ineligible reason
     */
    public StudentSelectionContextView {
        CourseValidation.text("termId", Objects.requireNonNull(termId, "termId"), 36);
        CourseValidation.text("termName", Objects.requireNonNull(termName, "termName"), 64);
        if (!Set.of("PLANNED", "ACTIVE", "CLOSED").contains(termStatus)) {
            throw new IllegalArgumentException("invalid term status");
        }
        Objects.requireNonNull(serverTime, "serverTime");
        boolean noPhase = phaseId == null && phaseType == null && displayTitle == null && phaseStatus == null;
        boolean completePhase = phaseId != null && phaseType != null && displayTitle != null && phaseStatus != null;
        if (!noPhase && !completePhase) throw new IllegalArgumentException("incomplete phase context");
        if (completePhase) {
            CourseValidation.text("phaseId", phaseId, 36);
            CourseValidation.text("displayTitle", displayTitle, 64);
            if (!Set.of("ENROLLMENT", "ADJUSTMENT").contains(phaseType)) {
                throw new IllegalArgumentException("invalid phase type");
            }
            if (!Set.of("PREVIEW", "OPEN").contains(phaseStatus)) {
                throw new IllegalArgumentException("invalid visible phase status");
            }
        }
        CourseValidation.optionalText("ineligibleReason", ineligibleReason, 128);
    }
}
