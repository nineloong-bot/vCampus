package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.Objects;

/** Requests preview or atomic admission of one fixed-format freshman CSV. */
public record FreshmanAdmissionCommand(String csv, int enrollmentYear) implements Serializable {
    /** Validates CSV content and the target freshman enrollment year. */
    public FreshmanAdmissionCommand {
        csv = Objects.requireNonNull(csv, "csv");
        if (csv.isBlank()) throw new IllegalArgumentException("csv must not be blank");
        if (enrollmentYear < 2000 || enrollmentYear > 2099)
            throw new IllegalArgumentException("enrollmentYear must be between 2000 and 2099");
    }
}
