package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Paged book search criteria. */
public record BookSearchQuery(String keyword, String category, Boolean availableOnly,
        int page, int pageSize) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
