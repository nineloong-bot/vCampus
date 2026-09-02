package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/** Creates one catalog title. */
public record CreateBookCommand(String isbn, String title, String author, String publisher,
        LocalDate publishDate, String category, String description) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
