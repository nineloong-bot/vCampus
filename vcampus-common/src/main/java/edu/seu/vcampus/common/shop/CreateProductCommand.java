package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * 商家发布新商品的请求命令。
 *  *
 *  * @param name 商品名称
 *  * @param description 商品描述
 *  * @param category 分类名称
 *  * @param imageUrl 图片地址
 */
public record CreateProductCommand(String productName, String category,
        String description, String coverImageUrl, List<CreateSkuCommand> skus) implements Serializable {
    public CreateProductCommand { skus = List.copyOf(skus); }

    public CreateProductCommand(String productName, String category, String description,
            List<CreateSkuCommand> skus) {
        this(productName, category, description, null, skus);
    }
}
