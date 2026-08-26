package edu.seu.vcampus.server.course.repository;

import java.time.DayOfWeek;

/** Database filters and pagination for offering discovery. */
public record OfferingSearchCriteria(String termId, String keyword, DayOfWeek dayOfWeek,
                                     boolean availableOnly, int page, int pageSize) {
    /** Validates zero-based paging input. */
    public OfferingSearchCriteria {
        if (page < 0 || pageSize < 1) throw new IllegalArgumentException("Invalid offering page");
    }
}
