package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record AdminCreateProductCommand(String shopId,
        CreateProductCommand command) implements Serializable { }
