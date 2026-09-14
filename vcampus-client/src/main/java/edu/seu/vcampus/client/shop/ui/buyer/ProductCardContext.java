package edu.seu.vcampus.client.shop.ui.buyer;/**
 * 商品卡片在不同展示场景下的上下文模式枚举（如推荐流、搜索结果）。
 */

public enum ProductCardContext {
    HOME(false), SEARCH(true), STOREFRONT(false);
    private final boolean showShopName;
    ProductCardContext(boolean showShopName) { this.showShopName = showShopName; }
    public boolean showShopName() { return showShopName; }
}
