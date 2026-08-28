package edu.seu.vcampus.client.core.navigation;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Registers and selects pages hosted by a Swing CardLayout container. */
public final class PageNavigator {
    private final JPanel container;
    private final CardLayout layout;
    private final Set<String> pageIds = new HashSet<>();

    /** Creates a navigator and installs CardLayout on the supplied container. */
    public PageNavigator(JPanel container) {
        this.container = Objects.requireNonNull(container, "container");
        this.layout = new CardLayout();
        container.setLayout(layout);
    }

    /** Adds a page under a unique identifier. */
    public void register(String pageId, JComponent page) {
        if (!pageIds.add(pageId)) {
            throw new IllegalArgumentException("Duplicate page id: " + pageId);
        }
        container.add(Objects.requireNonNull(page, "page"), pageId);
    }

    /** Shows a registered page, handing off to the EDT when necessary. */
    public void show(String pageId) {
        if (!pageIds.contains(pageId)) {
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
