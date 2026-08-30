package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/** Updates catalog metadata with optimistic version checking. */
public record UpdateBookCommand(String bookId, String isbn, String title, String author,
        String publisher, LocalDate publishDate, String category, String description,
        boolean active, long expectedVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
