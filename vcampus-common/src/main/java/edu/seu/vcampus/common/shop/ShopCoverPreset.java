package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** A selectable built-in product-cover placeholder. */
/**
 * Carries immutable shop cover preset data.
 * @param id the id
 * @param category the category
 * @param displayName the display name
 */
public record ShopCoverPreset(String id, String category, String displayName)
        implements Serializable { }
