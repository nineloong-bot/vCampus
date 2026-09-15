package edu.seu.vcampus.server.shop.order;

/** Public-safe order business failure code. */
public final class OrderException extends RuntimeException {
    /** Creates a failure carrying a stable client code. */
    public OrderException(String code) { super(code); }
}
