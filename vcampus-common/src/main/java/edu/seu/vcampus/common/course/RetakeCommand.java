package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Requests enrollment in an offering as a failed-course retake. */
/**
 * Carries immutable retake command data.
 * @param offeringId the offering identifier
 */
public record RetakeCommand(String offeringId) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /**
     * Validates and creates a retake command.
     * @param offeringId the offering identifier
     */
    public RetakeCommand {
        Objects.requireNonNull(offeringId, "offeringId");
        CourseValidation.text("offeringId", offeringId, 36);
    }
}
