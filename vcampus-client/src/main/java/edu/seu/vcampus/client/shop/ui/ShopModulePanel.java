package edu.seu.vcampus.client.shop.ui;

import javax.swing.JPanel;
import java.awt.CardLayout;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Hosts the fixed Shop pages inside the shared campus-shop placeholder. */
public final class ShopModulePanel extends JPanel implements ShopPageCoordinator.CardNavigator {
    private final CardLayout cards = new CardLayout();
    private final Set<String> pageIds = new HashSet<>();

    /** Creates the Shop-owned card container. */
    public ShopModulePanel() {
        super();
        setLayout(cards);
    }

    /** Registers a Shop page under one unique stable identifier. */
    @Override
    public void register(String pageId, JPanel page) {
        String id = Objects.requireNonNull(pageId, "pageId");
        if (!pageIds.add(id)) {
            throw new IllegalArgumentException("Duplicate Shop page id: " + id);
        }
        add(Objects.requireNonNull(page, "page"), id);
    }

    /** Restores a registered Shop page. */
    @Override
    public void show(String pageId) {
        String id = Objects.requireNonNull(pageId, "pageId");
        if (!pageIds.contains(id)) {
            throw new IllegalArgumentException("Unknown Shop page id: " + id);
        }
        cards.show(this, id);
    }
}
