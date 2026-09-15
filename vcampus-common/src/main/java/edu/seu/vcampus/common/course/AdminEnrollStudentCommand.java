package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Course-administrator request to place one eligible retake student into an offering. */
/**
 * Carries immutable admin enroll student command data.
 * @param studentNumber the student number
 * @param offeringId the offering identifier
 */
public record AdminEnrollStudentCommand(String studentNumber, String offeringId)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates the stable student number and target offering identifier. */
    public AdminEnrollStudentCommand {
        Objects.requireNonNull(studentNumber, "studentNumber");
        Objects.requireNonNull(offeringId, "offeringId");
        CourseValidation.text("studentNumber", studentNumber, 32);
        CourseValidation.text("offeringId", offeringId, 36);
    }
}
