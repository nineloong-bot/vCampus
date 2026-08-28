package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Saves a new or editable seller application draft. */
public record SaveSellerDraftCommand(String applicationId, String shopName,
        String description, String category, String contact,
        long expectedVersion) implements Serializable { }
