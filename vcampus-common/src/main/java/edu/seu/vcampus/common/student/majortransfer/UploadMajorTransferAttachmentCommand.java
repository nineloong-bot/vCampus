package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to upload an attachment to a transfer application. */
public record UploadMajorTransferAttachmentCommand(
        String applicationId,
        String fileName,
        String contentType,
        byte[] content,
        long expectedVersion
) implements Serializable {
    public UploadMajorTransferAttachmentCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");
        if (fileName.isBlank() || fileName.length() > 256) throw new IllegalArgumentException("附件文件名必填且不超过256字");
        content = content.clone();
    }
    @Override public byte[] content() { return content.clone(); }
}
