package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Administrative seller-application search criteria. */
public record SellerApplicationQuery(String applicantUserId,
        SellerApplicationStatus status, int pageNumber,
        int pageSize) implements Serializable { }
