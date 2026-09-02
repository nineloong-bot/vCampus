package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record UpdateCartItemCommand(String cartItemId, int quantity,
        long expectedVersion) implements Serializable { }
