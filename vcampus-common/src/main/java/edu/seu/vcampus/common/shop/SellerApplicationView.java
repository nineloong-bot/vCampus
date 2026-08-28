package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.time.Instant;

/** Serializable seller-application projection. */
public record SellerApplicationView(String applicationId, String applicantUserId,
        String shopName, String description, String category, String contact,
        SellerApplicationStatus status, String reviewReason, String reviewerUserId,
        Instant submittedAt, Instant reviewedAt,
        long rowVersion) implements Serializable { }
