package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/** Catalog metadata returned after an administration write. */
/**
 * Carries immutable book view data.
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
 */
public record BookView(String bookId, String isbn, String title, String author,
        String publisher, LocalDate publishDate, String category, String description,
        boolean active, long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
