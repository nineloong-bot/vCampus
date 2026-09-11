package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to generate the proposed admission list for an option. */
public record GenerateMajorTransferProposalCommand(
        String optionId,
        long expectedOptionVersion
) implements Serializable {
    public GenerateMajorTransferProposalCommand {
        Objects.requireNonNull(optionId, "optionId");
    }
}
