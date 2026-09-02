package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** One catalog row shown in book-search results. */
public record BookSummary(String bookId, String isbn, String title, String author,
        String category, int availableCopies, int totalCopies, boolean active)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public BookSummary(String bookId, String isbn, String title, String author,
            String category, int availableCopies, int totalCopies) {
        this(bookId, isbn, title, author, category, availableCopies, totalCopies, true);
    }
}
