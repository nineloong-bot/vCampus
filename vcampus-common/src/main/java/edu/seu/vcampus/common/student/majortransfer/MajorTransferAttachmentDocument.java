package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Authorized attachment download; filenames are display values, never server paths. */
public record MajorTransferAttachmentDocument(String fileName, String contentType, byte[] content) implements Serializable {
    public MajorTransferAttachmentDocument { content = content.clone(); }
    @Override public byte[] content() { return content.clone(); }
}
