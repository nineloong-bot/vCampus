package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品规格（SKU）信息视图对象。
 */
public record ProductSkuView(String skuId, String skuName, BigDecimal unitPrice,
        long availableQuantity, long stockQuantity, long reservedQuantity,
        boolean active, long rowVersion) implements Serializable {

    public ProductSkuView(String skuId, String skuName, BigDecimal unitPrice,
            long availableQuantity, boolean active, long rowVersion) {
        this(skuId, skuName, unitPrice, availableQuantity, availableQuantity, 0,
                active, rowVersion);
    }
}
