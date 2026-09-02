package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Requests an adjustment-window addition to one teaching offering. */
public record LateAddCommand(String offeringId) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public LateAddCommand {
        Objects.requireNonNull(offeringId, "offeringId");
        CourseValidation.text("offeringId", offeringId, 36);
    }
}
