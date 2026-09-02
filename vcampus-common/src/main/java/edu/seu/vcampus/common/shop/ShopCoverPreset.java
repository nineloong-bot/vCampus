package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** A selectable built-in product-cover placeholder. */
public record ShopCoverPreset(String id, String category, String displayName)
        implements Serializable { }
