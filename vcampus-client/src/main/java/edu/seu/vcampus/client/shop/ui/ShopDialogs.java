package edu.seu.vcampus.client.shop.ui;

/** User acknowledgement boundary for stable Shop errors. */
public interface ShopDialogs {
    void showError(String code);
    void confirm(String code, Runnable accepted);
}
