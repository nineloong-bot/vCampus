package edu.seu.vcampus.client.core.ui.autocomplete;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Loads autocomplete suggestions without blocking the Swing event thread. */
@FunctionalInterface
public interface SuggestionLoader {
    /** Loads at most {@code limit} choices matching the user's query. */
    CompletableFuture<List<AutocompleteChoice>> load(String query, int limit);
}
