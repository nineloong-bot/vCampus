package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Submits an owned draft for administrative review. */
public record SubmitSellerApplicationCommand(String applicationId,
        long expectedVersion) implements Serializable { }
