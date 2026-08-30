package edu.seu.vcampus.client.shop.ui;

import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.BorderLayout;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Hosts the fixed Shop pages inside the shared campus-shop placeholder. */
public final class ShopModulePanel extends JPanel implements ShopPageCoordinator.CardNavigator {
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final Set<String> pageIds = new HashSet<>();

    /** Creates the Shop-owned card container. */
    public ShopModulePanel() {
        super(new BorderLayout());
        pages.setName("shop.pages");
        add(pages, BorderLayout.CENTER);
    }

    /** Registers a Shop page under one unique stable identifier. */
    @Override
    public void register(String pageId, JPanel page) {
        String id = Objects.requireNonNull(pageId, "pageId");
        if (!pageIds.add(id)) {
            throw new IllegalArgumentException("Duplicate Shop page id: " + id);
        }
        pages.add(Objects.requireNonNull(page, "page"), id);
    }

    /** Restores a registered Shop page. */
    @Override
    public void show(String pageId) {
        String id = Objects.requireNonNull(pageId, "pageId");
        if (!pageIds.contains(id)) {
            throw new IllegalArgumentException("Unknown Shop page id: " + id);
        }
        cards.show(pages, id);
    }

    @Override
    public void installToolbar(ShopToolbar toolbar) {
        if (getComponentCount() != 1) {
            throw new IllegalStateException("Shop toolbar already installed");
        }
        add(Objects.requireNonNull(toolbar, "toolbar"), BorderLayout.NORTH);
    }
}
