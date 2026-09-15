package edu.seu.vcampus.client.core.ui.autocomplete;

import java.util.Objects;

/**
 * Stable suggestion returned by an autocomplete data source.
 *
 * @param id durable identifier submitted to the server
 * @param label primary text shown in the input
 * @param detail optional secondary text shown in the suggestion list
 */
public record AutocompleteChoice(String id, String label, String detail) {
    /** Validates the durable identifier and visible label. */
    public AutocompleteChoice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        detail = detail == null ? "" : detail;
    }
}
