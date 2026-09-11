package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;

/** Least-privilege lookup request for active teacher choices in course administration. */
public record CourseTeacherQuery(String keyword, int page, int pageSize) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public CourseTeacherQuery {
        CourseValidation.optionalText("keyword", keyword, 128);
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("invalid teacher query");
        }
    }
}
