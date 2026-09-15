package edu.seu.vcampus.common.shop;

/** Stable public error symbols for every shop workflow. */
public enum ShopErrorCode {
    /** Represents shop seller application exists. */ SHOP_SELLER_APPLICATION_EXISTS,
    /** Represents shop seller application status invalid. */ SHOP_SELLER_APPLICATION_STATUS_INVALID,
    /** Represents shop seller not approved. */ SHOP_SELLER_NOT_APPROVED,
    /** Represents shop name exists. */ SHOP_NAME_EXISTS,
    /** Represents shop concurrent modification. */ SHOP_CONCURRENT_MODIFICATION,
    /** Represents shop category invalid. */ SHOP_CATEGORY_INVALID,
    /** Represents shop not found. */ SHOP_NOT_FOUND,
    /** Represents shop not owner. */ SHOP_NOT_OWNER,
    /** Represents shop suspended. */ SHOP_SUSPENDED,
    /** Represents shop status invalid. */ SHOP_STATUS_INVALID,
    /** Represents shop product inactive. */ SHOP_PRODUCT_INACTIVE,
    /** Represents shop product name exists. */ SHOP_PRODUCT_NAME_EXISTS,
    /** Represents shop cover image url invalid. */ SHOP_COVER_IMAGE_URL_INVALID,
    /** Represents shop sku unavailable. */ SHOP_SKU_UNAVAILABLE,
    /** Represents shop price filter invalid. */ SHOP_PRICE_FILTER_INVALID,
    /** Represents shop price changed. */ SHOP_PRICE_CHANGED,
    /** Represents shop insufficient stock. */ SHOP_INSUFFICIENT_STOCK,
    /** Represents shop cart empty. */ SHOP_CART_EMPTY,
    /** Represents shop order status invalid. */ SHOP_ORDER_STATUS_INVALID,
    /** Represents shop order not owned. */ SHOP_ORDER_NOT_OWNED,
    /** Represents shop buyer forbidden. */ SHOP_BUYER_FORBIDDEN,
    /** Represents shop self purchase forbidden. */ SHOP_SELF_PURCHASE_FORBIDDEN,
    /** Represents payment already completed. */ PAYMENT_ALREADY_COMPLETED,
    /** Represents payment not pending. */ PAYMENT_NOT_PENDING,
    /** Represents payment amount mismatch. */ PAYMENT_AMOUNT_MISMATCH
}
