package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to upload an attachment to a transfer application. */
/**
 * Carries immutable upload major transfer attachment command data.
 * @param applicationId the application identifier
 * @param fileName the file name
 * @param contentType the content type
 * @param content the content
 * @param expectedVersion the expected version
 */
public record UploadMajorTransferAttachmentCommand(
        String applicationId,
        String fileName,
        String contentType,
        byte[] content,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a upload major transfer attachment command.
     * @param applicationId the application id
     * @param fileName the file name
     * @param contentType the content type
     * @param content the content
     * @param expectedVersion the expected version
     */
    public UploadMajorTransferAttachmentCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");
        if (fileName.isBlank() || fileName.length() > 256) throw new IllegalArgumentException("附件文件名必填且不超过256字");
        content = content.clone();
    }
    /**
 * Returns the content result.
 * @return the computed result
 */
@Override public byte[] content() { return content.clone(); }
}
