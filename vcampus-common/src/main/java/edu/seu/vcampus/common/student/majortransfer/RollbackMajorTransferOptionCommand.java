package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Requests rollback of one reviewed target-major option. */
public record RollbackMajorTransferOptionCommand(String optionId, long expectedOptionVersion)
        implements Serializable {
    /** Validates the option identity and optimistic version. */
    public RollbackMajorTransferOptionCommand {
        if (optionId == null || optionId.isBlank()) throw new IllegalArgumentException("optionId");
        if (expectedOptionVersion < 0) throw new IllegalArgumentException("expectedOptionVersion");
    }
}
