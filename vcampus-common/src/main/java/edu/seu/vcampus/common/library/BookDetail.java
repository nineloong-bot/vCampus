package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** Complete catalog title and its physical-copy availability. */
public record BookDetail(String bookId, String isbn, String title, String author,
        String publisher, LocalDate publishDate, String category, String description,
        boolean active, long rowVersion, List<BookCopyView> copies) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public BookDetail {
        copies = List.copyOf(copies);
    }
}
