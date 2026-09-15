package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** Complete catalog title and its physical-copy availability. */
/**
 * Carries immutable book detail data.
 * @param bookId the book identifier
 * @param isbn the isbn
 * @param title the title
 * @param author the author
 * @param publisher the publisher
 * @param publishDate the publish date
 * @param category the category
 * @param description the description
 * @param active the active
 * @param rowVersion the row version
 * @param copies the copies
 */
public record BookDetail(String bookId, String isbn, String title, String author,
        String publisher, LocalDate publishDate, String category, String description,
        boolean active, long rowVersion, List<BookCopyView> copies) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a book detail.
     * @param bookId the book identifier
     * @param isbn the isbn
     * @param title the title
     * @param author the author
     * @param publisher the publisher
     * @param publishDate the publish date
     * @param category the category
     * @param description the description
     * @param active the active
     * @param rowVersion the row version
     * @param copies the copies
     */
    public BookDetail {
        copies = List.copyOf(copies);
    }
}
