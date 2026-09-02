package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Administrative seller-application search criteria. */
public record SellerApplicationQuery(String applicantUserId,
        SellerApplicationListMode mode, int pageNumber,
        int pageSize) implements Serializable { }
