package edu.seu.vcampus.client.shop.ui.catalog;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/** Equal-width catalog grid whose preferred height accounts for wrapped rows. */
public final class WrappingGridLayout implements LayoutManager {
    private final int minimumCardWidth;
    private final int horizontalGap;
    private final int verticalGap;

    public WrappingGridLayout(int minimumCardWidth, int horizontalGap, int verticalGap) {
        this.minimumCardWidth = minimumCardWidth;
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
    }

    @Override public void addLayoutComponent(String name, Component component) { }
    @Override public void removeLayoutComponent(Component component) { }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int available = availableWidth(parent, insets);
            int columns = columns(available);
            int rows = rows(parent.getComponentCount(), columns);
            int cardHeight = maximumPreferredHeight(parent);
            int width = parent.getWidth() > 0
                    ? parent.getWidth()
                    : insets.left + insets.right
                            + columns * minimumCardWidth + (columns - 1) * horizontalGap;
            int height = insets.top + insets.bottom + rows * cardHeight
                    + Math.max(0, rows - 1) * verticalGap;
            return new Dimension(width, height);
        }
    }

    @Override public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int available = availableWidth(parent, insets);
            int columns = columns(available);
            int cardWidth = minimumCardWidth;
            int cardHeight = maximumPreferredHeight(parent);
            int usedWidth = columns * cardWidth + Math.max(0, columns - 1) * horizontalGap;
            int offset = Math.max(0, (available - usedWidth) / 2);
            for (int index = 0; index < parent.getComponentCount(); index++) {
                int column = index % columns;
                int row = index / columns;
                parent.getComponent(index).setBounds(
                        insets.left + offset + column * (cardWidth + horizontalGap),
                        insets.top + row * (cardHeight + verticalGap),
                        cardWidth, cardHeight);
            }
        }
    }

    private int availableWidth(Container parent, Insets insets) {
        int width = parent.getWidth() - insets.left - insets.right;
        if (width > 0) return width;
        Container ancestor = parent.getParent();
        while (ancestor != null) {
            if (ancestor.getWidth() > 0) return ancestor.getWidth();
            ancestor = ancestor.getParent();
        }
        return minimumCardWidth;
    }

    private int columns(int availableWidth) {
        return Math.max(1, (availableWidth + horizontalGap)
                / (minimumCardWidth + horizontalGap));
    }

    private static int rows(int count, int columns) {
        return count == 0 ? 0 : (count + columns - 1) / columns;
    }

    private static int maximumPreferredHeight(Container parent) {
        int height = 1;
        for (Component component : parent.getComponents()) {
            height = Math.max(height, component.getPreferredSize().height);
        }
        return height;
    }
}
