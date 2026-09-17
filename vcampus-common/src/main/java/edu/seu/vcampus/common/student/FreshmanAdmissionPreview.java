package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.List;

/** Read-only, server-validated class plan for a freshman admission CSV. */
public record FreshmanAdmissionPreview(int enrollmentYear,
                                       List<FreshmanClassAssignment> assignments)
        implements Serializable {
    /** Makes the preview assignment list immutable. */
    public FreshmanAdmissionPreview {
        if (enrollmentYear < 2000 || enrollmentYear > 2099)
            throw new IllegalArgumentException("invalid enrollmentYear");
        assignments = List.copyOf(assignments);
    }
}
