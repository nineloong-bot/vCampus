package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record UpdateShopCommand(String shopName, String description,
        String category, String contact, long expectedVersion) implements Serializable { }
