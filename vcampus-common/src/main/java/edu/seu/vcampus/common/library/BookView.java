package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/** Catalog metadata returned after an administration write. */
public record BookView(String bookId, String isbn, String title, String author,
        String publisher, LocalDate publishDate, String category, String description,
        boolean active, long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
