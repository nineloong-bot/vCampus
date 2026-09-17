package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Requests effectuation of one reviewed target-major option. */
public record EffectiveMajorTransferOptionCommand(String optionId, long expectedOptionVersion)
        implements Serializable {
    /** Validates the option identity and optimistic version. */
    public EffectiveMajorTransferOptionCommand {
        if (optionId == null || optionId.isBlank()) throw new IllegalArgumentException("optionId");
        if (expectedOptionVersion < 0) throw new IllegalArgumentException("expectedOptionVersion");
    }
}
