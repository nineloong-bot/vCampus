package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Changes a physical copy's operational state. */
public record ChangeCopyStatusCommand(String copyId, CopyStatus status, long expectedVersion)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
