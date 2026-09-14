package edu.seu.vcampus.common.shop.catalog;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Immutable payloads for the authenticated catalog, cart and import protocol. */
public final class CatalogDtos {
    private CatalogDtos() { }
    /** One-based pagination; sort is DEFAULT, PRICE_ASC/DESC or SALES_ASC/DESC. */
    public record Query(String keyword, String shopId, String sort, int page, int pageSize) implements Serializable { }
    /** Stable SKU identity; null price represents an unfinished draft field. */
    public record Sku(String id, String name, BigDecimal price, Integer totalStock,
            int reservedStock, boolean active) implements Serializable { }
    /** Product details; sales includes all SKU paid quantities less refunded quantities. */
    public record Product(String id, String shopId, String shopName, String name, String description,
            String category, String imageId, String status, String defaultSkuId, boolean deleted,
            int sales, List<Sku> skus) implements Serializable {
        /** Copies mutable input collections. */
        public Product { skus = List.copyOf(skus); }
    }
    /** A bounded page of products. */
    public record Page(List<Product> items, int total, int page, int pageSize) implements Serializable {
        /** Copies mutable input collections. */
        public Page { items = List.copyOf(items); }
    }
    /** Public shop data, including a suspension badge and no private owner information. */
    public record Shop(String id, String name, String description, String status) implements Serializable { }
    /** A bounded shop search result. */
    public record Shops(List<Shop> items, int total, int page, int pageSize) implements Serializable {
        /** Copies mutable input collections. */
        public Shops { items = List.copyOf(items); }
    }
    /** Save uses an empty product ID to create; new SKUs require caller-generated UUIDs. */
    public record SaveProduct(String requestKey, String id, String name, String description,
            String category, String imageId, String defaultSkuId, List<Sku> skus) implements Serializable {
        /** Copies mutable input collections. */
        public SaveProduct { skus = List.copyOf(skus); }
    }
    /** Lifecycle action: PUBLISH, OFF or DELETE. */
    public record ProductAction(String requestKey, String productId, String action) implements Serializable { }
    /** Assigns a preset image to owned drafts in one atomic batch. */
    public record Images(String requestKey, List<String> productIds, String imageId) implements Serializable {
        /** Copies mutable input collections. */
        public Images { productIds = List.copyOf(productIds); }
    }
    /** Add uses empty item ID; edit uses item ID; quantity zero removes an existing line. */
    public record CartChange(String requestKey, String itemId, String skuId, int quantity) implements Serializable { }
    /** Adds one of each product's explicit default SKU. */
    public record BulkAdd(String requestKey, List<String> productIds) implements Serializable {
        /** Copies mutable input collections. */
        public BulkAdd { productIds = List.copyOf(productIds); }
    }
    /** Cart lines expose current product data for price confirmation before checkout. */
    public record CartLine(String id, Product product, String skuId, int quantity) implements Serializable { }
    /** Current cart and explanations for skipped or capped quantities. */
    public record CartResult(List<CartLine> lines, List<String> notices) implements Serializable {
        /** Copies mutable input collections. */
        public CartResult { lines = List.copyOf(lines); notices = List.copyOf(notices); }
    }
    /** Raw spreadsheet cells with the original worksheet line number. */
    public record ImportRow(int line, String group, String name, String description, String category,
            String imageId, String skuName, String price, String stock, String defaultFlag) implements Serializable { }
    /** Import confirmation is bound to its request key and complete raw contents. */
    public record ImportCommand(String requestKey, List<ImportRow> rows) implements Serializable {
        /** Copies mutable input collections. */
        public ImportCommand { rows = List.copyOf(rows); }
    }
    /** Row status is VALID, ERROR or AFFECTED; warnings never prevent import. */
    public record ImportRowResult(ImportRow row, String status, List<String> errors, String warning) implements Serializable {
        /** Copies mutable input collections. */
        public ImportRowResult { errors = List.copyOf(errors); }
    }
    /** Full validation output; preview never creates business records. */
    public record ImportPreview(List<ImportRowResult> rows, int rowCount, int groupCount,
            int validGroups, int failedRows) implements Serializable {
        /** Copies mutable input collections. */
        public ImportPreview { rows = List.copyOf(rows); }
    }
    /** Persistent idempotent import outcome. */
    public record ImportResult(List<String> productIds, ImportPreview preview) implements Serializable {
        /** Copies mutable input collections. */
        public ImportResult { productIds = List.copyOf(productIds); }
    }
}
