package edu.seu.vcampus.client.course.ui;

import java.util.Objects;

/** Stable identifier plus a human-readable label used by offering reference selectors. */
public record OfferingReferenceChoice(String id, String label) {
    public OfferingReferenceChoice {
        id = Objects.requireNonNull(id, "id");
        label = Objects.requireNonNull(label, "label");
    }

    @Override public String toString() {
        return label;
    }
}
