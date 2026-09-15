package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;

/**
 * Administrator adjustment-audit filters and paging.
 *
 * @param studentNumber optional student-number filter
 * @param termId optional term identifier
 * @param adjustmentType optional adjustment type
 * @param operationResult optional operation result
 * @param page zero-based page number
 * @param pageSize number of rows per page
 */
public record AdjustmentAuditQuery(String studentNumber, String termId, String adjustmentType,
                                   String operationResult, int page, int pageSize)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates the query at the protocol boundary. */
    public AdjustmentAuditQuery {
        CourseValidation.optionalText("studentNumber", studentNumber, 32);
        CourseValidation.optionalText("termId", termId, 36);
        if (page < 0 || pageSize < 1 || pageSize > 100
                || (long) page * pageSize + pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid page");
        }
    }
}
