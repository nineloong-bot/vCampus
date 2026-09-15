package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;

import java.util.concurrent.CompletableFuture;

/** Resolves an exact ISBN to the library's stable book identifier. */
final class CopyBookLookup {
    private CopyBookLookup() { }

    static CompletableFuture<BookSummary> find(LibraryClientService service, String isbn, int page) {
        return service.searchManagedBooks(new BookSearchQuery(
                isbn, BookSearchField.ISBN, null, false, page, 100)).thenCompose(result -> {
            BookSummary match = result.items().stream().filter(item -> isbn.equals(item.isbn()))
                    .findFirst().orElse(null);
            if (match != null || result.items().isEmpty() || (long) page * 100 >= result.total()) {
                return CompletableFuture.completedFuture(match);
            }
            return find(service, isbn, page + 1);
        });
    }
}
