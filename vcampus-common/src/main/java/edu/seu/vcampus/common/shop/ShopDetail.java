package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record ShopDetail(String shopId, String shopName, String description,
        String category, String contact, ShopStatus shopStatus) implements Serializable { }
