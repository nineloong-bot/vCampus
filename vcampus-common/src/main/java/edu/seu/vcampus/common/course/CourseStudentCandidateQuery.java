package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;

/**
 * Paged student-number lookup used only for administrator class placement.
 *
 * @param studentNumber student-number fragment
 * @param page zero-based page number
 * @param pageSize number of candidates per page
 */
public record CourseStudentCandidateQuery(String studentNumber, int page,
                                          int pageSize) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates the optional number fragment and zero-based page. */
    public CourseStudentCandidateQuery {
        CourseValidation.optionalText("studentNumber", studentNumber, 32);
        if (page < 0 || pageSize < 1 || pageSize > 50
                || (long) page * pageSize + pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid page");
        }
    }
}
