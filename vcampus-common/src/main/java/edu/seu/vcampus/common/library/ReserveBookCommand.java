package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Requests a reservation for one currently unavailable physical copy. */
public record ReserveBookCommand(String copyId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
