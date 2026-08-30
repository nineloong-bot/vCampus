package edu.seu.vcampus.server.shop.demo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Deterministic product catalog for the authenticated Shop demo. */
public final class ShopDemoCatalog {
    private static final List<String> STATIONERY_NAMES = List.of(
            "黑色速干中性笔", "方格活页笔记本", "透明按动修正带", "双头荧光标记笔",
            "考试专用涂卡铅笔", "不锈钢学生剪刀", "透明刻度直尺", "可移除索引标签",
            "强力票据订书机", "帆布多层文具袋");
    private static final List<String> BOOK_NAMES = List.of(
            "Java 程序设计基础", "数据结构与算法实践", "计算机网络原理", "操作系统导论",
            "数据库系统概论", "软件工程项目实践", "离散数学学习指南", "高等数学方法与例题",
            "线性代数及其应用", "概率论与数理统计", "大学物理实验教程", "数字逻辑设计",
            "计算机组成原理", "编译原理入门", "人工智能基础", "机器学习实战导论",
            "Web 前端开发基础", "Python 数据分析", "Linux 系统管理", "信息安全概论",
            "大学英语学术写作", "英语四级词汇手册", "现代汉语写作", "中国近现代史纲要",
            "马克思主义基本原理", "大学生心理健康", "创新创业案例教程", "工程伦理导论",
            "设计思维与表达", "科研方法与论文写作");
    private static final List<String> DAILY_CATEGORIES = List.of(
            "纸巾", "洗衣液", "洗发水", "沐浴露", "牙膏", "毛巾", "水杯", "雨伞",
            "收纳盒", "垃圾袋", "清洁剂");
    private static final List<String> DAILY_SPECS = List.of(
            "便携装", "宿舍装", "家庭装", "清新款", "耐用款");
    private static final List<String> MEDICINE_NAMES = List.of(
            "医用退热贴", "防水创可贴", "碘伏消毒棉棒", "生理盐水鼻腔喷雾", "维生素 C 片");
    private static final List<ProductSeed> PRODUCTS = buildProducts();

    private ShopDemoCatalog() {
    }

    /** Returns the immutable, stable list of all one hundred demo products. */
    public static List<ProductSeed> products() {
        return PRODUCTS;
    }

    private static List<ProductSeed> buildProducts() {
        List<ProductSeed> products = new ArrayList<>(100);
        appendNamedProducts(products, "stationery", "demo-shop-stationery", "校园文具店",
                "文具", STATIONERY_NAMES, "课程记录、考试准备与日常整理", "标准规格", 250);
        appendNamedProducts(products, "books", "demo-shop-books", "校园书店",
                "图书", BOOK_NAMES, "课程阅读、课外拓展与知识检索", "平装版", 3200);
        appendDailyProducts(products);
        appendNamedProducts(products, "medicine", "demo-shop-medicine", "校园药店",
                "药品", MEDICINE_NAMES, "宿舍常备与个人日常护理", "独立包装", 680);
        return List.copyOf(products);
    }

    private static void appendNamedProducts(List<ProductSeed> products, String slug,
            String shopId, String shopName, String category, List<String> names,
            String purpose, String specification, long basePriceCents) {
        for (int index = 0; index < names.size(); index++) {
            addProduct(products, slug, index + 1, shopId, shopName, category,
                    names.get(index), purpose, specification, basePriceCents);
        }
    }

    private static void appendDailyProducts(List<ProductSeed> products) {
        int categoryIndex = 0;
        for (String dailyCategory : DAILY_CATEGORIES) {
            for (String specification : DAILY_SPECS) {
                int index = categoryIndex * DAILY_SPECS.size()
                        + DAILY_SPECS.indexOf(specification) + 1;
                addProduct(products, "daily", index, "demo-shop-daily", "校园生活超市",
                        "生活用品", dailyCategory + " " + specification,
                        dailyPurpose(dailyCategory), specification, 590 + categoryIndex * 135L);
            }
            categoryIndex++;
        }
    }

    private static String dailyPurpose(String category) {
        return switch (category) {
            case "纸巾", "毛巾" -> "宿舍清洁与个人擦拭";
            case "洗衣液", "清洁剂", "垃圾袋" -> "宿舍卫生与日常清洁";
            case "洗发水", "沐浴露", "牙膏" -> "个人洗护与清洁";
            case "水杯" -> "教室饮水与宿舍使用";
            case "雨伞" -> "校园通勤与雨天防护";
            case "收纳盒" -> "桌面整理与宿舍收纳";
            default -> "校园日常生活";
        };
    }

    private static void addProduct(List<ProductSeed> products, String slug, int categoryIndex,
            String shopId, String shopName, String category, String name, String purpose,
            String specification, long basePriceCents) {
        int globalIndex = products.size();
        String productId = "demo-%s-%03d".formatted(slug, categoryIndex);
        BigDecimal price = BigDecimal.valueOf(
                basePriceCents + categoryIndex * 37L + globalIndex % 7 * 11L, 2);
        long stock = 10L + globalIndex % 23;
        List<SkuSeed> skus = new ArrayList<>(2);
        skus.add(new SkuSeed(productId + "-sku-1", specification, price, stock));
        if ((globalIndex + 1) % 5 == 0) {
            skus.add(new SkuSeed(productId + "-sku-2", "组合装",
                    price.add(new BigDecimal("2.50")), 5L + globalIndex % 11));
        }
        String description = "%s，分类：%s；用途：%s；规格：%s。"
                .formatted(shopName, category, purpose, specification);
        products.add(new ProductSeed(productId, shopId, name, category, description,
                500L - globalIndex * 3L, skus));
    }

    /** A product and all of its sellable SKU fixtures. */
    public record ProductSeed(String id, String shopId, String name, String category,
            String description, long salesCount, List<SkuSeed> skus) {
        public ProductSeed {
            skus = List.copyOf(skus);
        }
    }

    /** A sellable deterministic SKU fixture. */
    public record SkuSeed(String id, String name, BigDecimal price, long stock) {
    }
}
