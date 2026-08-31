package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ShopErrorCode;

import java.net.URI;

final class ProductImageUrl {
    private static final int MAX_LENGTH = 2048;

    static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (value.length() > MAX_LENGTH) {
            throw invalid();
        }
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw invalid();
            }
            return uri.normalize().toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static RuntimeException invalid() {
        return SellerApplicationService.error(ShopErrorCode.SHOP_COVER_IMAGE_URL_INVALID,
                "Cover image URL must be an HTTPS URL without credentials");
    }

    private ProductImageUrl() { }
}
