package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to withdraw a submitted transfer application back to draft. */
public record WithdrawMajorTransferCommand(
        String applicationId,
        long expectedVersion
) implements Serializable {
    public WithdrawMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
