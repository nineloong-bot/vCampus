package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;

/** Least-privilege lookup request for active teacher choices in course administration. */
/**
 * Carries immutable course teacher query data.
 * @param keyword the keyword
 * @param page the page
 * @param pageSize the page size
 */
public record CourseTeacherQuery(String keyword, int page, int pageSize) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a course teacher query.
     * @param keyword the keyword
     * @param page the page
     * @param pageSize the page size
     */
    public CourseTeacherQuery {
        CourseValidation.optionalText("keyword", keyword, 128);
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("invalid teacher query");
        }
    }
}
