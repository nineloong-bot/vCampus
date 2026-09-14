package edu.seu.vcampus.client.shop.ui.catalog;

import javax.swing.ImageIcon;
import java.awt.Dimension;
import java.util.concurrent.CompletableFuture;

/**
 * 商城商品缩略图与详情图异步加载器契约接口。
 */
@FunctionalInterface
public interface ProductImageLoader extends AutoCloseable {
    CompletableFuture<ImageIcon> load(String coverImageUrl, String category, Dimension target);

    @Override
    default void close() { }
}
