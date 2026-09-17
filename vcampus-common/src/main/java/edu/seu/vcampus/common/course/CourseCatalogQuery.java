package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;

/** Catalog filters and paging. */
public record CourseCatalogQuery(String keyword, Boolean activeOnly, String departmentId,
                                 String departmentName, int page, int pageSize)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates bounded paging. */
    public CourseCatalogQuery {
        if (page < 0 || pageSize < 1 || pageSize > 100
                || (long) page * pageSize + pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid page");
        }
    }

    /** Compatibility constructor for the former visible-name filter. */
    public CourseCatalogQuery(String keyword, Boolean activeOnly, String departmentName,
                              int page, int pageSize) {
        this(keyword, activeOnly, null, departmentName, page, pageSize);
    }

    /** Creates an unscoped catalog query. */
    public CourseCatalogQuery(String keyword, Boolean activeOnly, int page, int pageSize) {
        this(keyword, activeOnly, null, null, page, pageSize);
    }
}
