package edu.seu.vcampus.common.shop.order;

import java.io.Serializable;

/** List filter: ALL, CLOSED, or an exact lifecycle state; seller may scope to own shop. */
public record OrderQuery(String state, String shopId) implements Serializable { }
