package edu.seu.vcampus.server.library.domain;

import java.time.LocalDate;

/** Catalog metadata shared by every physical copy of a title. */
public record Book(String bookId, String isbn, String title, String author, String publisher,
        LocalDate publishDate, String category, String description, boolean active,
        long rowVersion) {
}
