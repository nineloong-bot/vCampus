package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Assessed fine with its wallet receipt identifier, absent until successfully paid. */
public record LibraryFineView(LoanView loan, String paymentId) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Whether the shared wallet journal contains a completed payment. */
    public boolean paid() {
        return paymentId != null;
    }
}
