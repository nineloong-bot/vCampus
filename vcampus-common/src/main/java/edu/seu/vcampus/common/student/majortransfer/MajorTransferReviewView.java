package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.time.Instant;

/** Immutable view of one review record. */
public record MajorTransferReviewView(
        String reviewId,
        String applicationId,
        MajorTransferReviewStage reviewStage,
        MajorTransferDecision decision,
        String reviewerUserId,
        String comment,
        Boolean sourceVerified,
        Boolean noMisconduct,
        Boolean admissionAllowed,
        Instant createdAt
) implements Serializable { }
