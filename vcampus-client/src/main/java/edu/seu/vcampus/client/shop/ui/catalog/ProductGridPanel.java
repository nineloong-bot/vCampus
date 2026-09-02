package edu.seu.vcampus.client.shop.ui.catalog;

import edu.seu.vcampus.common.shop.ProductSummary;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Catalog composition boundary: data/navigation stay independent of card visuals. */
public final class ProductGridPanel extends JPanel {
    private static final Dimension IMAGE_SIZE = new Dimension(160, 110);
    private final ProductImageLoader images;
    private final ProductCardRenderer renderer;
    private final Consumer<String> openDetail;
    private final List<String> names = new ArrayList<>();
    private long renderVersion;

    public ProductGridPanel(ProductImageLoader images, ProductCardRenderer renderer,
            Consumer<String> openDetail) {
        super(new WrappingGridLayout(200, 12, 12));
        this.images = Objects.requireNonNull(images, "images");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.openDetail = Objects.requireNonNull(openDetail, "openDetail");
    }

    public void showProducts(List<ProductSummary> products) {
        removeAll();
        names.clear();
        long currentVersion = ++renderVersion;
        for (ProductSummary product : products) {
            names.add(product.productName());
            JPanel slot = new JPanel(new BorderLayout());
            add(slot);
            renderInto(slot, product, null);
            images.load(product.coverImageUrl(), product.category(), IMAGE_SIZE)
                    .exceptionally(ignored -> null)
                    .thenAccept(image -> SwingUtilities.invokeLater(() -> {
                        if (currentVersion == renderVersion) {
                            renderInto(slot, product, image);
                        }
                    }));
        }
        revalidate();
        repaint();
    }

    public List<String> visibleProductNames() { return List.copyOf(names); }

    public void dispose() { images.close(); }

    private void renderInto(JPanel slot, ProductSummary product, ImageIcon image) {
        JComponent card = renderer.render(product, image, () -> openDetail.accept(product.productId()));
        slot.removeAll();
        slot.add(card);
        slot.revalidate();
        slot.repaint();
        revalidate();
        if (getParent() != null) getParent().revalidate();
    }
}
