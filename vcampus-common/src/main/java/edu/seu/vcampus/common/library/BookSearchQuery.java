package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Paged book search criteria. */
/**
 * Carries immutable book search query data.
 * @param keyword the keyword
 * @param field the field
 * @param category the category
 * @param availableOnly the available only
 * @param page the page
 * @param pageSize the page size
 */
public record BookSearchQuery(String keyword, BookSearchField field, String category, Boolean availableOnly,
        int page, int pageSize) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a book search query.
     * @param keyword the keyword
     * @param category the category
     * @param availableOnly the available only
     * @param page the page
     * @param pageSize the page size
     */
    public BookSearchQuery(String keyword, String category, Boolean availableOnly,
            int page, int pageSize) {
        this(keyword, BookSearchField.ANY, category, availableOnly, page, pageSize);
    }
}
