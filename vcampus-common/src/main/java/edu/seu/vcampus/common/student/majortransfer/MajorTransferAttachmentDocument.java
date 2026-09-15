package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Authorized attachment download; filenames are display values, never server paths. */
/**
 * Carries immutable major transfer attachment document data.
 * @param fileName the file name
 * @param contentType the content type
 * @param content the content
 */
public record MajorTransferAttachmentDocument(String fileName, String contentType, byte[] content) implements Serializable {
    /**
 * Validates and creates a major transfer attachment document.
 * @param fileName the file name
 * @param contentType the content type
 * @param content the content
 */
public MajorTransferAttachmentDocument { content = content.clone(); }
    /**
 * Returns the content result.
 * @return the computed result
 */
@Override public byte[] content() { return content.clone(); }
}
