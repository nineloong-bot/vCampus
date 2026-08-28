package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

public record UpdateProductCommand(String productId, String productName,
        String category, String description, List<UpsertSkuCommand> skus,
        long expectedVersion) implements Serializable {
    public UpdateProductCommand { skus = List.copyOf(skus); }
}
