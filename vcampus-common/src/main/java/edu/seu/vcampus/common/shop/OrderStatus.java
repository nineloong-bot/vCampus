package edu.seu.vcampus.common.shop;

/** Defines supported order status values. */
public enum OrderStatus {
    /** Represents pending payment. */ PENDING_PAYMENT,
    /** Represents paid. */ PAID,
    /** Represents preparing. */ PREPARING,
    /** Represents shipped. */ SHIPPED,
    /** Represents completed. */ COMPLETED,
    /** Represents cancelled. */ CANCELLED
}
