package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/** One teaching-class choice with a student-specific action and reason. */
public record TeachingClassOptionView(OfferingSummary offering, String actionType, String actionReason)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public TeachingClassOptionView {
        Objects.requireNonNull(offering, "offering");
        if (!Set.of("ENROLL", "RETAKE", "LATE_ADD", "SELECTED", "UNAVAILABLE").contains(actionType)) {
            throw new IllegalArgumentException("invalid teaching class action");
        }
        CourseValidation.optionalText("actionReason", actionReason, 128);
    }
}
