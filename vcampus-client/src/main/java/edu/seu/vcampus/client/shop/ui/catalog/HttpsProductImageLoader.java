package edu.seu.vcampus.client.shop.ui.catalog;

import javax.swing.ImageIcon;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

/** Safe local fallback until a replaceable network image provider is supplied. */
public final class HttpsProductImageLoader implements ProductImageLoader {
    @Override
    public CompletableFuture<ImageIcon> load(String coverImageUrl, String category, Dimension target) {
        int width = Math.max(1, target.width);
        int height = Math.max(1, target.height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(UiColors.BACKGROUND_SUBTLE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return CompletableFuture.completedFuture(new ImageIcon(image));
    }
}
