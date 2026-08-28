package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record ChangeProductStatusCommand(String productId,
        ProductStatus targetStatus, long expectedVersion) implements Serializable { }
