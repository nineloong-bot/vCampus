package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Query to list applications for admin review. */
public record MajorTransferApplicationQuery(
        String batchId,
        String optionId,
        MajorTransferStatus status
) implements Serializable { }
