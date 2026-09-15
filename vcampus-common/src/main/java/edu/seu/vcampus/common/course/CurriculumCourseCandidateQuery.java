package edu.seu.vcampus.common.course;

import java.io.Serializable;

/** Keyword and paging input for curriculum-backed catalog candidates. */
public record CurriculumCourseCandidateQuery(String keyword, int page, int pageSize)
        implements Serializable {
    public CurriculumCourseCandidateQuery {
        if (page < 0 || pageSize < 1 || pageSize > 100
                || (long) page * pageSize + pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid page");
        }
    }
}
