package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Requests final review of one target-major option. */
public record FinalizeMajorTransferOptionCommand(String optionId, long expectedOptionVersion)
        implements Serializable {
    /** Validates the option identity and optimistic version. */
    public FinalizeMajorTransferOptionCommand {
        if (optionId == null || optionId.isBlank()) throw new IllegalArgumentException("optionId");
        if (expectedOptionVersion < 0) throw new IllegalArgumentException("expectedOptionVersion");
    }
}
