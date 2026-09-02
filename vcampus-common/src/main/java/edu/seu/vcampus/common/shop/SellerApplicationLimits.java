package edu.seu.vcampus.common.shop;

/** User-facing length limits shared by seller-application clients and services. */
public final class SellerApplicationLimits {
    public static final int SHOP_NAME = 50;
    public static final int CONTACT = 50;
    public static final int APPLICATION_STATEMENT = 500;

    private SellerApplicationLimits() { }

    public static void validate(String shopName, String contact, String applicationStatement) {
        requireWithin(shopName, SHOP_NAME, "店铺名称");
        requireWithin(contact, CONTACT, "联系方式");
        requireWithin(applicationStatement, APPLICATION_STATEMENT, "经营计划");
    }

    private static void requireWithin(String value, int limit, String label) {
        if (value != null && value.strip().codePointCount(0, value.strip().length()) > limit) {
            throw new IllegalArgumentException(label + "不能超过 " + limit + " 字");
        }
    }
}
