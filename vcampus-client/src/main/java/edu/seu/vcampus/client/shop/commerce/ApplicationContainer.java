package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import java.awt.*;

/** Centers the application page while keeping narrow viewports vertically scrollable. */
final class ApplicationContainer extends JPanel implements Scrollable {
    private final JComponent content;
    private final int maximumWidth;

    ApplicationContainer(JComponent content, int maximumWidth) {
        super(null);
        this.content = content;
        this.maximumWidth = maximumWidth;
        setOpaque(false);
        add(content);
    }

    @Override public void doLayout() {
        int width = Math.min(maximumWidth, getWidth());
        content.setBounds((getWidth() - width) / 2, 0, width, content.getPreferredSize().height);
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(maximumWidth, content.getPreferredSize().height);
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
