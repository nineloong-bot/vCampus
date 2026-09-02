package edu.seu.vcampus.client.shop.ui;

import java.util.Map;

/** Extracts the stable code returned by the Shop protocol. */
public final class ShopUiErrors {
    private static final String FALLBACK = "操作失败，请稍后重试";
    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("SHOP_SELLER_APPLICATION_EXISTS", "你已经有开店申请，请刷新后查看"),
            Map.entry("SHOP_SELLER_APPLICATION_STATUS_INVALID", "当前申请状态不允许此操作，请刷新后重试"),
            Map.entry("SHOP_SELLER_NOT_APPROVED", "当前账号尚未获得店主权限"),
            Map.entry("SHOP_NAME_EXISTS", "该店铺名称已被使用，请更换名称"),
            Map.entry("SHOP_CONCURRENT_MODIFICATION", "数据已被更新，请刷新后重试"),
            Map.entry("SHOP_CATEGORY_INVALID", "请选择有效的店铺类别"),
            Map.entry("SHOP_NOT_FOUND", "店铺不存在或已不可用"),
            Map.entry("SHOP_NOT_OWNER", "你没有权限操作该店铺或商品"),
            Map.entry("SHOP_SUSPENDED", "店铺已暂停营业，当前操作不可用"),
            Map.entry("SHOP_STATUS_INVALID", "当前状态不允许此操作，请刷新后重试"),
            Map.entry("SHOP_PRODUCT_INACTIVE", "商品不存在或已下架"),
            Map.entry("SHOP_PRODUCT_NAME_EXISTS", "店铺中已存在同名商品，请更换名称"),
            Map.entry("SHOP_COVER_IMAGE_URL_INVALID", "请选择有效的商品封面"),
            Map.entry("SHOP_SKU_UNAVAILABLE", "商品种类当前不可用，请检查状态和库存"),
            Map.entry("SHOP_PRICE_FILTER_INVALID", "价格筛选条件无效，请重新填写"),
            Map.entry("SHOP_PRICE_CHANGED", "商品价格已变化，请确认最新价格"),
            Map.entry("SHOP_INSUFFICIENT_STOCK", "商品库存不足，请调整购买数量"),
            Map.entry("SHOP_CART_EMPTY", "购物车为空，请先选择商品"),
            Map.entry("SHOP_ORDER_STATUS_INVALID", "当前订单状态不允许此操作"),
            Map.entry("SHOP_ORDER_NOT_OWNED", "你没有权限查看或操作该订单"),
            Map.entry("SHOP_BUYER_FORBIDDEN", "管理员账号不能执行购买操作"),
            Map.entry("SHOP_SELF_PURCHASE_FORBIDDEN", "不能购买自己店铺中的商品"),
            Map.entry("PAYMENT_ALREADY_COMPLETED", "该订单已经支付完成"),
            Map.entry("PAYMENT_NOT_PENDING", "支付已结束或已失效，请刷新订单状态"),
            Map.entry("PAYMENT_AMOUNT_MISMATCH", "支付金额与订单不一致，请重新发起支付"),
            Map.entry("AUTH_SESSION_EXPIRED", "登录状态已失效，请重新登录"),
            Map.entry("COMMON_VALIDATION_FAILED", "填写内容不符合要求，请检查后重试"),
            Map.entry("COMMON_INTERNAL_ERROR", FALLBACK),
            Map.entry("NETWORK_TIMEOUT", "请求超时，请检查网络后重试"),
            Map.entry("NETWORK_CONNECTION_FAILED", "无法连接服务器，请检查服务是否启动"));

    private ShopUiErrors() { }

    public static String code(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "COMMON_INTERNAL_ERROR" : cause.getMessage();
    }

    public static boolean sessionExpired(String code) {
        return "AUTH_SESSION_EXPIRED".equals(code);
    }

    public static String message(Throwable failure) {
        return message(code(failure));
    }

    public static String message(String code) {
        return MESSAGES.getOrDefault(code, FALLBACK);
    }
}
