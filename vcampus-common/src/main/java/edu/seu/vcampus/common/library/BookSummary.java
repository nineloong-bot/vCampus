package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** One catalog row shown in book-search results. */
/**
 * Carries immutable book summary data.
 * @param bookId the book identifier
 * @param isbn the isbn
 * @param title the title
 * @param author the author
 * @param category the category
 * @param availableCopies the available copies
 * @param totalCopies the total copies
 * @param active the active
 */
public record BookSummary(String bookId, String isbn, String title, String author,
        String category, int availableCopies, int totalCopies, boolean active)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a book summary.
     * @param bookId the book identifier
     * @param isbn the isbn
     * @param title the title
     * @param author the author
     * @param category the category
     * @param availableCopies the available copies
     * @param totalCopies the total copies
     */
    public BookSummary(String bookId, String isbn, String title, String author,
            String category, int availableCopies, int totalCopies) {
        this(bookId, isbn, title, author, category, availableCopies, totalCopies, true);
    }
}
