package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.time.Instant;

/** Serializable seller-application projection. */
/**
 * Carries immutable seller application view data.
 * @param applicationId the application identifier
 * @param applicantUserId the applicant user identifier
 * @param shopName the shop name
 * @param description the description
 * @param category the category
 * @param contact the contact
 * @param applicationStatement the application statement
 * @param status the status
 * @param reviewReason the review reason
 * @param reviewerUserId the reviewer user identifier
 * @param submittedAt the submitted at
 * @param reviewedAt the reviewed at
 * @param rowVersion the row version
 */
public record SellerApplicationView(String applicationId, String applicantUserId,
        String shopName, String description, String category, String contact,
        String applicationStatement,
        SellerApplicationStatus status, String reviewReason, String reviewerUserId,
        Instant submittedAt, Instant reviewedAt,
        long rowVersion) implements Serializable { }
