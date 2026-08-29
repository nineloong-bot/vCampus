package edu.seu.vcampus.client.shop.ui.style;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.LayoutManager;

/** Semantic construction boundary for buyer Shop pages. */
public interface ShopUiKit {
    JButton primaryButton(String name, String text);
    JButton secondaryButton(String name, String text);
    JPanel filterPanel(String name, LayoutManager layout);
    JPanel productCard(String name, LayoutManager layout);
    JComponent stateView(String name, ShopPageState state, String message, Runnable retry);
}
