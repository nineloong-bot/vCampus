package edu.seu.vcampus.common.paging;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Immutable page of serializable results.
 * @param <T> item representation
 * @param items items in this page
 * @param page zero-based page number
 * @param pageSize requested page size
 * @param total total matching item count
 */
public record PageResult<T extends Serializable>(
        List<T> items,
        int page,
        int pageSize,
        long total) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Defensively copies page items to preserve immutability.
     * @param items items in this page
     * @param page zero-based page number
     * @param pageSize requested page size
     * @param total total matching item count
     */
    public PageResult {
        items = List.copyOf(items);
    }
}
