package edu.seu.vcampus.server.shop.catalog;

/** A safe, user-visible catalog validation failure. */
public final class CatalogException extends RuntimeException {
    /** Creates a failure containing a business explanation only. */
    public CatalogException(String message) { super(message); }
}
