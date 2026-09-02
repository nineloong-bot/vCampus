package edu.seu.vcampus.server.shop.domain;

import edu.seu.vcampus.common.shop.SellerApplicationStatus;

import java.io.Serializable;
import java.time.Instant;

/** Persistence model for a seller application. */
public record SellerApplication(String applicationId, String applicantUserId,
        String shopName, String description, String category, String contact,
        String applicationStatement,
        SellerApplicationStatus status, String reviewReason, String reviewerUserId,
        Instant submittedAt, Instant reviewedAt, long rowVersion) implements Serializable { }
