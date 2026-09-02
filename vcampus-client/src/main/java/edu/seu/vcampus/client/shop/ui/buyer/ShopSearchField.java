package edu.seu.vcampus.client.shop.ui.buyer;

import javax.swing.JTextField;

/** Shop-owned search input with a visible, stable placeholder. */
final class ShopSearchField extends JTextField {
    static final String PLACEHOLDER = "搜索商品、店铺或相关信息……";

    ShopSearchField(int columns, String name) {
        super(columns);
        setName(name);
        putClientProperty("JTextField.placeholderText", PLACEHOLDER);
        putClientProperty("shop.placeholderText", PLACEHOLDER);
        getAccessibleContext().setAccessibleName("商城搜索");
        getAccessibleContext().setAccessibleDescription(PLACEHOLDER);
    }
}
