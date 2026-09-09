package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to delete an attachment from a transfer application. */
public record DeleteMajorTransferAttachmentCommand(
        String attachmentId,
        String applicationId,
        long expectedVersion
) implements Serializable {
    public DeleteMajorTransferAttachmentCommand {
        Objects.requireNonNull(attachmentId, "attachmentId");
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
