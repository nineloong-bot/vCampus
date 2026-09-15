package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Administrative seller-application search criteria. */
/**
 * Carries immutable seller application query data.
 * @param applicantUserId the applicant user identifier
 * @param mode the mode
 * @param pageNumber the page number
 * @param pageSize the page size
 */
public record SellerApplicationQuery(String applicantUserId,
        SellerApplicationListMode mode, int pageNumber,
        int pageSize) implements Serializable { }
