package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Requests normal enrollment in one teaching offering. */
public record EnrollCommand(String offeringId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Rejects an absent or blank offering identifier at the message boundary. */
    public EnrollCommand {
        Objects.requireNonNull(offeringId, "offeringId");
        if (offeringId.isBlank()) {
            throw new IllegalArgumentException("offeringId must not be blank");
        }
    }
}
