package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.List;

/** Parsed freshman CSV rows or all safe validation errors. */
public record FreshmanAdmissionCsvResult(List<FreshmanAdmissionRow> rows,
                                         List<FreshmanAdmissionValidationError> errors)
        implements Serializable {
    /** Makes the result immutable and hides rows whenever validation failed. */
    public FreshmanAdmissionCsvResult {
        rows = List.copyOf(rows);
        errors = List.copyOf(errors);
        if (!errors.isEmpty()) rows = List.of();
    }
}
