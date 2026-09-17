package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/**
 * Downloadable CSV template for major-transfer score entry.
 *
 * @param fileName suggested file name
 * @param content  CSV content bytes
 */
public record MajorTransferScoreTemplateDocument(
        String fileName,
        byte[] content
) implements Serializable {

    /**
     * Constructs a validated and defensive document payload.
     *
     * @param fileName suggested file name
     * @param content  CSV content bytes
     */
    public MajorTransferScoreTemplateDocument {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");
        content = content.clone();
    }

    /**
     * Returns a copy of the CSV content bytes.
     *
     * @return cloned content bytes
     */
    @Override
    public byte[] content() {
        return content.clone();
    }
}
