package edu.seu.vcampus.client.shop.ui.style;

import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.LayoutManager;

/** Semantic construction boundary for buyer Shop pages. */
public interface ShopUiKit {
    JButton navigationButton(String name, String text);
    JButton primaryButton(String name, String text);
    JButton secondaryButton(String name, String text);
    JPanel filterPanel(String name, LayoutManager layout);
    JPanel productCard(String name, LayoutManager layout);
    default JPanel spacedProductCard(String name, LayoutManager layout, int bottomGap) {
        JPanel card = productCard(name, layout);
        card.setBorder(BorderFactory.createCompoundBorder(card.getBorder(),
                BorderFactory.createEmptyBorder(0, 0, bottomGap, 0)));
        return card;
    }
    JComponent stateView(String name, ShopPageState state, String message, Runnable retry);
}
