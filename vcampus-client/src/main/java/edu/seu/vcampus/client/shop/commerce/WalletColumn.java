package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import java.awt.*;

/** Natural wallet column whose explicit gap components match the prototype spacing. */
final class WalletColumn extends JPanel {
    WalletColumn() {
        super(new ColumnLayout());
        setOpaque(false);
    }

    private static final class ColumnLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets padding = parent.getInsets();
            int width = 0;
            int height = 0;
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                Dimension size = child.getPreferredSize();
                width = Math.max(width, size.width);
                height += size.height;
            }
            return new Dimension(width + padding.left + padding.right, height + padding.top + padding.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }

        @Override public void layoutContainer(Container parent) {
            Insets padding = parent.getInsets();
            int y = padding.top;
            int width = Math.max(0, parent.getWidth() - padding.left - padding.right);
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                int height = child.getPreferredSize().height;
                child.setBounds(padding.left, y, width, height);
                y += height;
            }
        }
    }
}
