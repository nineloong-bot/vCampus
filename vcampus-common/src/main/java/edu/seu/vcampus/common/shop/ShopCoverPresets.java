package edu.seu.vcampus.common.shop;

import java.util.List;
import java.util.Optional;

/** Canonical no-database-upgrade cover choices shared by Shop clients and services. */
public final class ShopCoverPresets {
    private static final List<ShopCoverPreset> ALL = List.of(
            preset("stationery/writing-1", "文具", "书写工具"),
            preset("stationery/notebook-1", "文具", "笔记用品"),
            preset("stationery/ruler-1", "文具", "测量工具"),
            preset("stationery/marker-1", "文具", "标记用品"),
            preset("books/textbook-1", "图书", "教材"), preset("books/reading-1", "图书", "课外阅读"),
            preset("books/reference-1", "图书", "工具书"), preset("books/literature-1", "图书", "文学"),
            preset("daily/cleaning-1", "生活用品", "清洁用品"), preset("daily/storage-1", "生活用品", "收纳用品"),
            preset("daily/drinkware-1", "生活用品", "饮水用品"), preset("daily/care-1", "生活用品", "日常护理"),
            preset("medicine/first-aid-1", "药品", "应急护理"), preset("medicine/cold-care-1", "药品", "感冒护理"),
            preset("medicine/pain-care-1", "药品", "疼痛护理"), preset("medicine/health-1", "药品", "健康用品"),
            preset("other/digital-1", "其他", "数码用品"), preset("other/sports-1", "其他", "运动用品"),
            preset("other/gift-1", "其他", "礼品"), preset("other/general-1", "其他", "通用商品"));

    public static List<ShopCoverPreset> all() { return ALL; }

    public static List<ShopCoverPreset> forCategory(String category) {
        String supported = ShopCategories.requireSupported(category);
        return ALL.stream().filter(value -> value.category().equals(supported)).toList();
    }

    public static Optional<ShopCoverPreset> find(String id) {
        return ALL.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    private static ShopCoverPreset preset(String path, String category, String name) {
        return new ShopCoverPreset("builtin://shop/" + path, category, name);
    }

    private ShopCoverPresets() { }
}
