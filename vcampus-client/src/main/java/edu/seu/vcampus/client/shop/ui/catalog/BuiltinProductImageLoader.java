package edu.seu.vcampus.client.shop.ui.catalog;

import edu.seu.vcampus.common.shop.ShopCoverPreset;
import edu.seu.vcampus.common.shop.ShopCoverPresets;

import javax.swing.ImageIcon;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

/** Offline renderer for built-in cover IDs, delegating legacy values to the existing loader. */
public final class BuiltinProductImageLoader implements ProductImageLoader {
    private final ProductImageLoader legacy = new HttpsProductImageLoader();

    @Override public CompletableFuture<ImageIcon> load(String id, String category, Dimension target) {
        return ShopCoverPresets.find(id == null ? "" : id)
                .map(preset -> CompletableFuture.completedFuture(render(preset, target)))
                .orElseGet(() -> legacy.load(id, category, target));
    }

    private static ImageIcon render(ShopCoverPreset preset, Dimension target) {
        int width = Math.max(1, target.width), height = Math.max(1, target.height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        int hue = Math.floorMod(preset.category().hashCode(), 360);
        graphics.setColor(Color.getHSBColor(hue / 360f, .18f, .96f)); graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(55, 72, 90)); graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 18f));
        graphics.drawString("▧", Math.max(8, width / 2 - 9), Math.max(24, height / 2));
        graphics.setFont(graphics.getFont().deriveFont(Font.PLAIN, 12f));
        graphics.drawString(preset.displayName(), 8, height - 12); graphics.dispose();
        return new ImageIcon(image);
    }

    @Override public void close() { legacy.close(); }
}
