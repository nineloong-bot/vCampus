package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

public record CreateProductCommand(String productName, String category,
        String description, List<CreateSkuCommand> skus) implements Serializable {
    public CreateProductCommand { skus = List.copyOf(skus); }
}
