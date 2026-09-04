package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/** Creates one catalog title. */
public record CreateBookCommand(String isbn, String title, String author, String publisher,
        LocalDate publishDate, String category, String description, String locationCode, String barcode) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public CreateBookCommand(String isbn, String title, String author, String publisher,
            LocalDate publishDate, String category, String description, String locationCode) {
        this(isbn, title, author, publisher, publishDate, category, description, locationCode, null);
    }

    public CreateBookCommand(String isbn, String title, String author, String publisher,
            LocalDate publishDate, String category, String description) {
        this(isbn, title, author, publisher, publishDate, category, description, null, null);
    }
}
