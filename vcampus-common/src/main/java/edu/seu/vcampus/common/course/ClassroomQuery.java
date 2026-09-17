package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;

/** Filters used by the classroom autocomplete endpoint. */
public record ClassroomQuery(String keyword, int minimumCapacity, int limit) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates the bounded search request. */
    public ClassroomQuery {
        if (minimumCapacity < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("invalid classroom query");
        }
    }
}
