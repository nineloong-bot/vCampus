package edu.seu.vcampus.server.course.repository;

import java.util.List;

/** One page of offering search results and the matching database row count. */
public record OfferingSearchPage(List<Offering> items, long total) {
    /** Defensively freezes the result collection for callers. */
    public OfferingSearchPage {
        items = List.copyOf(items);
    }
}
