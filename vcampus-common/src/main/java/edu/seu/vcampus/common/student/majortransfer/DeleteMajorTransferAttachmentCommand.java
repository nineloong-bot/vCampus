package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to delete an attachment from a transfer application. */
/**
 * Carries immutable delete major transfer attachment command data.
 * @param attachmentId the attachment identifier
 * @param applicationId the application identifier
 * @param expectedVersion the expected version
 */
public record DeleteMajorTransferAttachmentCommand(
        String attachmentId,
        String applicationId,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a delete major transfer attachment command.
     * @param attachmentId the attachment identifier
     * @param applicationId the application identifier
     * @param expectedVersion the expected version
     */
    public DeleteMajorTransferAttachmentCommand {
        Objects.requireNonNull(attachmentId, "attachmentId");
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
