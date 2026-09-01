package edu.seu.vcampus.client.core.navigation;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Registers and selects pages hosted by a Swing CardLayout container. */
public final class PageNavigator {
    private final JPanel container;
    private final CardLayout layout;
    private final Map<String, JComponent> pages = new HashMap<>();

    /** Creates a navigator and installs CardLayout on the supplied container. */
    public PageNavigator(JPanel container) {
        this.container = Objects.requireNonNull(container, "container");
        this.layout = new CardLayout();
        container.setLayout(layout);
    }

    /** Adds a page under a unique identifier. */
    public void register(String pageId, JComponent page) {
        if (pages.containsKey(pageId)) {
            throw new IllegalArgumentException("Duplicate page id: " + pageId);
        }
        JComponent registered = Objects.requireNonNull(page, "page");
        pages.put(pageId, registered);
        container.add(registered, pageId);
    }

    /** Replaces a registered page without changing its navigation identifier. */
    public void replace(String pageId, JComponent page) {
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(page, "page");
        JComponent previous = pages.get(pageId);
        if (previous == null) throw new IllegalArgumentException("Unknown page id: " + pageId);
        container.remove(previous);
        pages.put(pageId, page);
        container.add(page, pageId);
        container.revalidate();
        container.repaint();
    }

    /** Shows a registered page, handing off to the EDT when necessary. */
    public void show(String pageId) {
        if (!pages.containsKey(pageId)) {
            throw new IllegalArgumentException("Unknown page id: " + pageId);
        }
        Runnable display = () -> layout.show(container, pageId);
        if (SwingUtilities.isEventDispatchThread()) {
            display.run();
        } else {
            SwingUtilities.invokeLater(display);
        }
    }
}
