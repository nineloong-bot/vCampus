package edu.seu.vcampus.common.shop;

import java.util.List;
import java.util.Objects;

/** Supported shop categories shared by catalog clients and services. */
public final class ShopCategories {
    public static final List<String> ALL = List.of("文具", "图书", "生活用品", "药品", "其他");

    public static String requireSupported(String value) {
        String normalized = Objects.requireNonNull(value, "category").strip();
        if (!ALL.contains(normalized)) {
            throw new IllegalArgumentException("unsupported category");
        }
        return normalized;
    }

    private ShopCategories() { }
}
