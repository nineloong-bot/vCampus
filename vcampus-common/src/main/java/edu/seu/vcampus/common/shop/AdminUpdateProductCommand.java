package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record AdminUpdateProductCommand(String shopId,
        UpdateProductCommand command) implements Serializable { }
