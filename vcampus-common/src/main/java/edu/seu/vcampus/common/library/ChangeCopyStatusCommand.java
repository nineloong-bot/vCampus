package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Changes a physical copy's operational state. */
/**
 * Carries immutable change copy status command data.
 * @param copyId the copy identifier
 * @param status the status
 * @param expectedVersion the expected version
 */
public record ChangeCopyStatusCommand(String copyId, CopyStatus status, long expectedVersion)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
