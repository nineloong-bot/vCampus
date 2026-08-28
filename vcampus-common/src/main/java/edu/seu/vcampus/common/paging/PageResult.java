package edu.seu.vcampus.common.paging;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** Immutable page of serializable results. */
public record PageResult<T extends Serializable>(
        List<T> items,
        int page,
        int pageSize,
        long total) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Defensively copies page items to preserve immutability. */
    public PageResult {
        items = List.copyOf(items);
    }
}
