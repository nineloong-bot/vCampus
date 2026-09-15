package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/** Creates one catalog title. */
/**
 * Carries immutable create book command data.
 * @param isbn the isbn
 * @param title the title
 * @param author the author
 * @param publisher the publisher
 * @param publishDate the publish date
 * @param category the category
 * @param description the description
 * @param locationCode the location code
 * @param barcode the barcode
 */
public record CreateBookCommand(String isbn, String title, String author, String publisher,
        LocalDate publishDate, String category, String description, String locationCode, String barcode) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a create book command.
     * @param isbn the isbn
     * @param title the title
     * @param author the author
     * @param publisher the publisher
     * @param publishDate the publish date
     * @param category the category
     * @param description the description
     * @param locationCode the location code
     */
    public CreateBookCommand(String isbn, String title, String author, String publisher,
            LocalDate publishDate, String category, String description, String locationCode) {
        this(isbn, title, author, publisher, publishDate, category, description, locationCode, null);
    }

    /**
     * Validates and creates a create book command.
     * @param isbn the isbn
     * @param title the title
     * @param author the author
     * @param publisher the publisher
     * @param publishDate the publish date
     * @param category the category
     * @param description the description
     */
    public CreateBookCommand(String isbn, String title, String author, String publisher,
            LocalDate publishDate, String category, String description) {
        this(isbn, title, author, publisher, publishDate, category, description, null, null);
    }
}
