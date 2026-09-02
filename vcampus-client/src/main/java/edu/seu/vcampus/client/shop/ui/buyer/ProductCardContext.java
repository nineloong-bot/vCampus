package edu.seu.vcampus.client.shop.ui.buyer;
public enum ProductCardContext {
    HOME(false), SEARCH(true), STOREFRONT(false);
    private final boolean showShopName;
    ProductCardContext(boolean showShopName) { this.showShopName = showShopName; }
    public boolean showShopName() { return showShopName; }
}
