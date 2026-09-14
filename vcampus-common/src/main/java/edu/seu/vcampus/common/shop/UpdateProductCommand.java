package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * 更新商品信息的请求命令。
 *  *
 *  * @param productId 商品标识
 *  * @param name 商品名称
 *  * @param description 商品描述
 *  * @param category 分类名称
 *  * @param imageUrl 图片地址
 */
public record UpdateProductCommand(String productId, String productName,
        String category, String description, String coverImageUrl, List<UpsertSkuCommand> skus,
        long expectedVersion) implements Serializable {
    public UpdateProductCommand { skus = List.copyOf(skus); }

    public UpdateProductCommand(String productId, String productName, String category,
            String description, List<UpsertSkuCommand> skus, long expectedVersion) {
        this(productId, productName, category, description, null, skus, expectedVersion);
    }
}
