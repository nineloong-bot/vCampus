package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/** Updates catalog metadata with optimistic version checking. */
/**
 * Carries immutable update book command data.
 * @param bookId the book identifier
 * @param isbn the isbn
 * @param title the title
 * @param author the author
 * @param publisher the publisher
 * @param publishDate the publish date
 * @param category the category
 * @param description the description
 * @param active the active
 * @param expectedVersion the expected version
 */
public record UpdateBookCommand(String bookId, String isbn, String title, String author,
        String publisher, LocalDate publishDate, String category, String description,
        boolean active, long expectedVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
