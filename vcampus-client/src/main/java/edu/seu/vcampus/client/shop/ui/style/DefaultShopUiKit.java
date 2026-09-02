package edu.seu.vcampus.client.shop.ui.style;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.LayoutManager;

/** Default UIManager-respecting Shop component factory. */
public final class DefaultShopUiKit implements ShopUiKit {
    @Override
    public JButton navigationButton(String name, String text) { return named(new JButton(text), name); }

    @Override
    public JButton primaryButton(String name, String text) { return named(new JButton(text), name); }

    @Override
    public JButton secondaryButton(String name, String text) { return named(new JButton(text), name); }

    @Override
    public JPanel filterPanel(String name, LayoutManager layout) { return named(new JPanel(layout), name); }

    @Override
    public JPanel productCard(String name, LayoutManager layout) { return named(new JPanel(layout), name); }

    @Override
    public JComponent stateView(String name, ShopPageState state, String message,
            Runnable retry) {
        JPanel panel = named(new JPanel(new FlowLayout()), name);
        panel.add(new JLabel(message));
        if (retry != null) {
            JButton button = secondaryButton(name + ".retry", "重试");
            button.addActionListener(event -> retry.run());
            panel.add(button);
        }
        return panel;
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
