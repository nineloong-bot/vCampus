package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.Objects;

/** The deterministic class assigned to one freshman CSV row. */
public record FreshmanClassAssignment(FreshmanAdmissionRow row, int classNumber,
                                      String className) implements Serializable {
    /** Validates one class assignment. */
    public FreshmanClassAssignment {
        row = Objects.requireNonNull(row, "row");
        if (classNumber < 1 || classNumber > 99) throw new IllegalArgumentException("invalid classNumber");
        className = Objects.requireNonNull(className, "className").trim();
        if (className.isEmpty()) throw new IllegalArgumentException("className must not be blank");
    }
}
