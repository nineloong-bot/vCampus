package edu.seu.vcampus.server.shop.catalog;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.persistence.SqlWork;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import static edu.seu.vcampus.server.shop.catalog.CatalogSql.*;

/** Catalog application boundary sharing the transaction manager with orders and governance. */
public final class CatalogService {
    final TransactionManager transactions;
    final Clock clock;
    final CatalogStore store = new CatalogStore();
    final ProductWriter writer;
    private final ResourceLockManager locks;
    private final ProductPolicy policy;
    /** Uses the shared transaction and lock managers and authoritative governance policy. */
    public CatalogService(TransactionManager transactions, ResourceLockManager locks, Clock clock, ProductPolicy policy) {
        this.transactions = transactions; this.locks = locks; this.clock = clock; this.policy = policy;
        writer = new ProductWriter(store, policy);
    }
    /** Checks visibility, retaining zero-stock products so buyers can see that they are sold out. */
    public boolean purchasable(Connection c, String id) throws SQLException {
        Product p = store.get(c, id);
        return !p.deleted() && p.status().equals("ACTIVE")
                && "ACTIVE".equals(scalar(c, "SELECT shopStatus FROM tblShop WHERE shopId=?", p.shopId()))
                && policy.mayBuy(c, id);
    }
    /** Verifies completeness before governance restores a qualification-blocked product. */
    public boolean mayRestore(Connection c, String id) throws SQLException {
        if (!purchasable(c, id)) return false;
        try { ProductRules.publish(store.get(c, id)); return true; }
        catch (CatalogException error) { return false; }
    }
    /** Provides read-only oversight; the authenticated handler restricts this to administrators. */
    public Page adminList(Query query) { return transactions.inTransaction(c -> list(c, "\u0000ADMIN", query)); }
    /** Provides preserved product information for administrator oversight. */
    public Product adminDetail(String id) { return transactions.inTransaction(c -> view(c, store.get(c, id))); }
    /** Searches public products using default-SKU price and product-wide net sales. */
    public Page list(Query query) { return transactions.inTransaction(c -> list(c, null, query)); }
    /** Searches the authenticated owner's products including drafts and withdrawals. */
    public Page ownedList(String user, Query query) { return transactions.inTransaction(c -> list(c, user, query)); }
    /** Returns details only while the product is visible to buyers. */
    public Product detail(String id) {
        return transactions.inTransaction(c -> {
            require(purchasable(c, id), "商品暂不可浏览");
            return view(c, store.get(c, id));
        });
    }
    /** Returns the authenticated seller's editable product. */
    public Product ownedDetail(String user, String id) { return transactions.inTransaction(c -> view(c, store.owned(c, user, id))); }
    /** Returns shops including stopped shops; their public product pages remain empty. */
    public Shops shops(Query q) {
        bounds(q);
        return transactions.inTransaction(c -> {
            var all = store.shops(c).stream().filter(s -> match(s.name(), q.keyword())).toList();
            int start = Math.min(all.size(), (q.page() - 1) * q.pageSize());
            return new Shops(all.subList(start, Math.min(all.size(), start + q.pageSize())), all.size(), q.page(), q.pageSize());
        });
    }
    /** Retrieves public shop details even if its trading is suspended. */
    public Shop shop(String id) {
        return transactions.inTransaction(c -> store.shops(c).stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new CatalogException("店铺不存在")));
    }
    /** Saves a name-only draft or atomically edits an existing product and stable SKUs. */
    public Product save(String user, SaveProduct command) {
        return write(user, "SAVE", command.requestKey(), command, c -> writer.save(c, user, command, clock.instant()));
    }
    /** Publishes, withdraws or irreversibly soft-deletes an owned product. */
    public Product action(String user, ProductAction command) {
        return write(user, "ACTION", command.requestKey(), command, c -> writer.action(c, user, command, clock.instant()));
    }
    /** Applies a preset cover to all selected owned drafts in a single transaction. */
    public Boolean images(String user, Images command) {
        return write(user, "IMAGES", command.requestKey(), command, c -> {
            require(ProductRules.IMAGES.contains(text(command.imageId())), "图片编号不存在");
            require(!command.productIds().isEmpty() && command.productIds().size() <= 1000, "请选择商品");
            for (String id : command.productIds()) {
                var p = store.owned(c, user, id);
                require(p.status().equals("DRAFT"), "批量选图仅用于草稿");
                store.metadata(c, id, p.defaultSkuId(), command.imageId(), false);
            }
            return true;
        });
    }
    <T extends Serializable> T write(String user, String op, String request, Serializable command, SqlWork<T> work) {
        String key = key(user, op, request);
        return locks.withLocks(List.of(new ResourceKey("CATALOG", "GLOBAL")), () -> transactions.inTransaction(c -> {
            Serializable old = replay(c, key, command);
            if (old != null) {
                @SuppressWarnings("unchecked") T typed = (T) old;
                return typed;
            }
            T result = work.apply(c);
            receipt(c, key, command, result);
            return result;
        }));
    }
    private Page list(Connection c, String user, Query q) throws SQLException {
        bounds(q);
        boolean admin = "\u0000ADMIN".equals(user);
        String ownerShop = user == null || admin ? null : store.ownerShop(c, user);
        var result = new ArrayList<Product>();
        for (String id : store.ids(c)) {
            Product p = store.get(c, id);
            if (p.deleted() || !match(p.name(), q.keyword())) continue;
            if (ownerShop != null && !ownerShop.equals(p.shopId())) continue;
            if (!admin && ownerShop == null && (!text(q.shopId()).isEmpty() && !q.shopId().equals(p.shopId()) || !purchasable(c, id))) continue;
            result.add(view(c, p));
        }
        Comparator<Product> sort = switch (text(q.sort())) {
            case "PRICE_ASC" -> Comparator.comparing(CatalogService::price);
            case "PRICE_DESC" -> Comparator.comparing(CatalogService::price).reversed();
            case "SALES_ASC" -> Comparator.comparingInt(Product::sales);
            case "SALES_DESC" -> Comparator.comparingInt(Product::sales).reversed();
            default -> null;
        };
        if (sort != null) result.sort(sort.thenComparing(Product::id));
        int start = Math.min(result.size(), (q.page() - 1) * q.pageSize());
        return new Page(result.subList(start, Math.min(result.size(), start + q.pageSize())), result.size(), q.page(), q.pageSize());
    }
    private static BigDecimal price(Product p) {
        return p.skus().stream().filter(s -> s.id().equals(p.defaultSkuId())).map(Sku::price)
                .filter(java.util.Objects::nonNull).findFirst().orElse(BigDecimal.ZERO);
    }
    private Product view(Connection c, Product p) throws SQLException {
        return new Product(p.id(), p.shopId(), p.shopName(), p.name(), p.description(), p.category(), p.imageId(),
                policy.effectiveStatus(c, p.id(), p.status()), p.defaultSkuId(), p.deleted(), p.sales(), p.skus());
    }
    private static boolean match(String value, String keyword) {
        return text(value).toLowerCase(Locale.ROOT).contains(text(keyword).toLowerCase(Locale.ROOT));
    }
    private static void bounds(Query q) {
        require(q != null && q.page() >= 1 && q.page() <= 1000000 && q.pageSize() >= 1 && q.pageSize() <= 100,
                "分页参数无效");
        require(SetHolder.SORTS.contains(text(q.sort())), "排序参数无效");
    }
    private static final class SetHolder {
        private static final java.util.Set<String> SORTS = java.util.Set.of("", "DEFAULT", "PRICE_ASC", "PRICE_DESC", "SALES_ASC", "SALES_DESC");
    }
}
