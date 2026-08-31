package edu.seu.vcampus.client.shop.ui.catalog;

import javax.swing.ImageIcon;
import java.awt.Dimension;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ProductImageLoader extends AutoCloseable {
    CompletableFuture<ImageIcon> load(String coverImageUrl, String category, Dimension target);

    @Override
    default void close() { }
}
