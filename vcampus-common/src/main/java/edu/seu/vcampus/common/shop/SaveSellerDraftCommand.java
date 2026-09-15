package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Saves a new or editable seller application draft. */
/**
 * Carries immutable save seller draft command data.
 * @param applicationId the application identifier
 * @param shopName the shop name
 * @param description the description
 * @param category the category
 * @param contact the contact
 * @param applicationStatement the application statement
 * @param expectedVersion the expected version
 */
public record SaveSellerDraftCommand(String applicationId, String shopName,
        String description, String category, String contact,
        String applicationStatement,
        long expectedVersion) implements Serializable { }
