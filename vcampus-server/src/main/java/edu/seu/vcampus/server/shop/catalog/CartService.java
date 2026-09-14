package edu.seu.vcampus.server.shop.catalog;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import static edu.seu.vcampus.server.shop.catalog.CatalogSql.*;

/** Persistent buyer cart with current availability checks and stable SKU merging. */
public final class CartService {
    private final CatalogService catalog;
    /** Shares catalog transactions, clocks and governance checks. */
    public CartService(CatalogService catalog) { this.catalog = catalog; }
    /** Loads the cart, removing products and specifications that are no longer offered. */
    public CartResult get(String user) { return catalog.transactions.inTransaction(c -> current(c, user, new ArrayList<>())); }
    /** Adds or edits a SKU; editing to quantity zero removes that line. */
    public CartResult change(String user, CartChange command) {
        return catalog.write(user, "CART", command.requestKey(), command, c -> {
            var notices = new ArrayList<String>();
            String cart = cart(c, user);
            require(command.quantity() >= 0, "数量须为非负整数");
            String item = text(command.itemId());
            String oldSku = null;
            if (!item.isEmpty()) {
                oldSku = scalar(c, "SELECT skuId FROM tblCartItem WHERE cartItemId=? AND cartId=?", item, cart);
                require(oldSku != null, "购物车条目不存在");
            }
            if (command.quantity() == 0) {
                require(oldSku != null, "请选择需要移除的条目");
                update(c, "DELETE FROM tblCartItem WHERE cartItemId=?", item);
            } else {
                String product = scalar(c, "SELECT productId FROM tblProductSku WHERE skuId=?", command.skuId());
                require(product != null, "规格不存在");
                if (oldSku != null) {
                    String oldProduct = scalar(c, "SELECT productId FROM tblProductSku WHERE skuId=?", oldSku);
                    require(product.equals(oldProduct), "只能切换同一商品的规格");
                    if (oldSku.equals(command.skuId())) {
                        require(catalog.purchasable(c, product), "商品暂不可购买");
                        var sku = catalog.store.skus(c, product).stream().filter(s -> s.id().equals(command.skuId()) && s.active())
                                .findFirst().orElseThrow(() -> new CatalogException("规格已停用"));
                        int available = sku.totalStock() == null ? 0 : sku.totalStock() - sku.reservedStock();
                        int next = Math.min(available, command.quantity());
                        if (next == 0) update(c, "DELETE FROM tblCartItem WHERE cartItemId=?", item);
                        else update(c, "UPDATE tblCartItem SET quantity=?,rowVersion=rowVersion+1 WHERE cartItemId=?", next, item);
                        if (next < command.quantity()) notices.add("数量已调整至最多可买的" + next + "件");
                        return current(c, user, notices);
                    }
                    update(c, "DELETE FROM tblCartItem WHERE cartItemId=?", item);
                }
                add(c, cart, command.skuId(), command.quantity(), notices, false);
            }
            return current(c, user, notices);
        });
    }
    /** Adds exactly one of each selected product's default SKU and reports every skipped product. */
    public CartResult bulk(String user, BulkAdd command) {
        return catalog.write(user, "BULK", command.requestKey(), command, c -> {
            require(!command.productIds().isEmpty() && command.productIds().size() <= 1000, "请选择1至1000件商品");
            String cart = cart(c, user);
            var notices = new ArrayList<String>();
            for (String id : new LinkedHashSet<>(command.productIds())) {
                try {
                    var p = catalog.store.get(c, id);
                    if (!catalog.purchasable(c, id)) { notices.add(p.name() + "：已跳过，商品不可购买"); continue; }
                    add(c, cart, p.defaultSkuId(), 1, notices, true);
                } catch (CatalogException ex) { notices.add("已跳过：" + ex.getMessage()); }
            }
            return current(c, user, notices);
        });
    }
    private void add(Connection c, String cart, String skuId, int quantity, List<String> notices, boolean bulk) throws SQLException {
        String product = scalar(c, "SELECT productId FROM tblProductSku WHERE skuId=?", skuId);
        require(product != null && catalog.purchasable(c, product), "商品暂不可购买");
        var p = catalog.store.get(c, product);
        var sku = p.skus().stream().filter(s -> s.id().equals(skuId) && s.active()).findFirst()
                .orElseThrow(() -> new CatalogException("规格已停用"));
        int available = sku.totalStock() == null ? 0 : sku.totalStock() - sku.reservedStock();
        if (available <= 0) {
            notices.add(p.name() + "：已跳过，所选规格缺货");
            return;
        }
        String existing = scalar(c, "SELECT cartItemId FROM tblCartItem WHERE cartId=? AND skuId=?", cart, skuId);
        int previous = existing == null ? 0 : Integer.parseInt(scalar(c, "SELECT quantity FROM tblCartItem WHERE cartItemId=?", existing));
        int next = (int) Math.min(available, (long) previous + quantity);
        if ((long) previous + quantity > available) notices.add(p.name() + "：数量已调整至最多可买的" + available + "件");
        var now = Timestamp.from(catalog.clock.instant());
        if (existing == null) update(c, "INSERT INTO tblCartItem(cartItemId,cartId,skuId,quantity,rowVersion,createdAt,updatedAt) "
                + "VALUES(?,?,?,?,0,?,?)", UUID.randomUUID().toString(), cart, skuId, next, now, now);
        else update(c, "UPDATE tblCartItem SET quantity=?,updatedAt=?,rowVersion=rowVersion+1 WHERE cartItemId=?", next, now, existing);
        if (bulk) notices.add(p.name() + "：已加入购物车");
    }
    private CartResult current(Connection c, String user, List<String> notices) throws SQLException {
        String cart = scalar(c, "SELECT cartId FROM tblCart WHERE userId=?", user);
        if (cart == null) return new CartResult(List.of(), notices);
        var lines = new ArrayList<CartLine>();
        var remove = new ArrayList<String>();
        try (var s = prepare(c, "SELECT i.cartItemId,i.skuId,i.quantity,s.productId,s.isActive "
                + "FROM tblCartItem i INNER JOIN tblProductSku s ON i.skuId=s.skuId WHERE i.cartId=? ORDER BY i.createdAt", cart);
             var r = s.executeQuery()) {
            while (r.next()) {
                String id = r.getString(1);
                if (!r.getBoolean(5) || !catalog.purchasable(c, r.getString(4))) { remove.add(id); continue; }
                lines.add(new CartLine(id, catalog.store.get(c, r.getString(4)), r.getString(2), r.getInt(3)));
            }
        }
        for (String id : remove) update(c, "DELETE FROM tblCartItem WHERE cartItemId=?", id);
        if (!remove.isEmpty()) notices.add("已移除" + remove.size() + "项不可购买商品");
        return new CartResult(lines, notices);
    }
    private String cart(Connection c, String user) throws SQLException {
        String id = scalar(c, "SELECT cartId FROM tblCart WHERE userId=?", user);
        if (id == null) {
            id = UUID.randomUUID().toString();
            update(c, "INSERT INTO tblCart(cartId,userId,updatedAt) VALUES(?,?,?)", id, user, Timestamp.from(catalog.clock.instant()));
        }
        return id;
    }
}
