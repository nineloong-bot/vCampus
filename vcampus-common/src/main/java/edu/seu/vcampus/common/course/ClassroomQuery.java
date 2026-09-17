package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;

/** Classroom autocomplete filters. */
public record ClassroomQuery(String keyword, int minimumCapacity, int limit) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates the bounded classroom search. */
    public ClassroomQuery {
        if (minimumCapacity < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("invalid classroom query");
    }
}
