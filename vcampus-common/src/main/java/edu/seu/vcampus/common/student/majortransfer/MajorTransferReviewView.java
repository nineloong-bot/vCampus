package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.time.Instant;

/** Immutable view of one review record. */
/**
 * Carries immutable major transfer review view data.
 * @param reviewId the review identifier
 * @param applicationId the application identifier
 * @param reviewStage the review stage
 * @param decision the decision
 * @param reviewerUserId the reviewer user identifier
 * @param comment the comment
 * @param sourceVerified the source verified
 * @param noMisconduct the no misconduct
 * @param admissionAllowed the admission allowed
 * @param createdAt the created at
 */
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
