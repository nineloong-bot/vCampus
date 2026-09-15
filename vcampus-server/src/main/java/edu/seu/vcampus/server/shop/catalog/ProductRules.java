package edu.seu.vcampus.server.shop.catalog;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import static edu.seu.vcampus.server.shop.catalog.CatalogSql.*;

final class ProductRules {
    static final Set<String> IMAGES = Set.of("book", "pen", "cup", "bag", "shirt", "box");
    private ProductRules() { }
    static void price(BigDecimal price) {
        require(price.signum() > 0 && price.scale() <= 2 && price.compareTo(new BigDecimal("9999999999.99")) <= 0,
                "价格须大于0、最多两位小数且不超过金额上限");
    }
    static void draft(SaveProduct p) {
        require(!text(p.name()).isEmpty() && text(p.name()).length() <= 200, "商品名称须为1至200字");
        require(text(p.description()).length() <= 10000, "介绍不能超过10000字");
        require(text(p.category()).isEmpty() || Set.of("ordinary", "licensed").contains(text(p.category())), "准入类目无效");
        require(text(p.imageId()).isEmpty() || IMAGES.contains(text(p.imageId())), "图片编号不存在");
        require(p.skus().size() <= 1000, "每个商品最多1000个规格");
        var ids = new HashSet<String>();
        var names = new HashSet<String>();
        for (var sku : p.skus()) {
            require(!text(sku.id()).isEmpty() && sku.id().length() <= 36 && ids.add(sku.id()), "规格编号缺失或重复");
            require(text(sku.name()).length() <= 128, "规格名称过长");
            if (!text(sku.name()).isEmpty()) require(names.add(text(sku.name())), "规格名称重复");
            if (sku.price() != null) price(sku.price());
            require(sku.totalStock() == null || sku.totalStock() >= 0, "库存须为非负整数");
        }
        require(p.skus().isEmpty() || p.skus().stream().anyMatch(s -> s.active() && s.id().equals(p.defaultSkuId())),
                "请选择一个有效默认规格");
    }
    static void publish(Product p) {
        var errors = new java.util.ArrayList<String>();
        if (text(p.name()).isEmpty()) errors.add("缺少商品名称");
        if (text(p.description()).isEmpty()) errors.add("缺少商品介绍");
        if (!IMAGES.contains(text(p.imageId()))) errors.add("请选择有效图片");
        if (!Set.of("ordinary", "licensed").contains(text(p.category()))) errors.add("请选择准入类目");
        if (p.skus().stream().noneMatch(s -> s.active() && s.id().equals(p.defaultSkuId()))) errors.add("缺少默认规格");
        for (var s : p.skus()) if (s.active() && (text(s.name()).isEmpty() || s.price() == null
                || s.price().signum() <= 0 || s.totalStock() == null)) errors.add("规格信息不完整：" + s.name());
        require(errors.isEmpty(), String.join("；", errors));
    }
}
