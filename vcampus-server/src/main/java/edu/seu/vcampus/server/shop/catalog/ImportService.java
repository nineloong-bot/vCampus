package edu.seu.vcampus.server.shop.catalog;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import static edu.seu.vcampus.server.shop.catalog.CatalogSql.*;

/** Server-authoritative, group-atomic structured Excel preview and durable confirmation. */
public final class ImportService {
    private final CatalogService catalog;
    private final ImportValidator validator = new ImportValidator();
    /** Shares the catalog's storage and atomic write boundary. */
    public ImportService(CatalogService catalog) { this.catalog = catalog; }
    /** Validates raw rows without writing product, receipt or preview records. */
    public ImportPreview preview(String user, ImportCommand command) {
        return catalog.transactions.inTransaction(c -> preview(c, user, command));
    }
    /** Revalidates all raw data and creates only complete valid groups as drafts, exactly once. */
    public ImportResult confirm(String user, ImportCommand command) {
        return catalog.write(user, "IMPORT", command.requestKey(), command, c -> {
            var preview = preview(c, user, command);
            var groups = new LinkedHashMap<String, List<ImportRow>>();
            for (var result : preview.rows()) if (result.status().equals("VALID"))
                groups.computeIfAbsent(text(result.row().group()), ignored -> new ArrayList<>()).add(result.row());
            var ids = new ArrayList<String>();
            for (var rows : groups.values()) {
                var first = rows.getFirst();
                var skus = new ArrayList<Sku>();
                String defaultId = null;
                for (var row : rows) {
                    String id = UUID.randomUUID().toString();
                    skus.add(new Sku(id, text(row.skuName()), new BigDecimal(text(row.price())),
                            Integer.parseInt(text(row.stock())), 0, true));
                    if (text(row.defaultFlag()).equals("是")) defaultId = id;
                }
                var draft = new SaveProduct(command.requestKey(), "", text(first.name()), text(first.description()),
                        "ordinary", text(first.imageId()), defaultId, skus);
                ids.add(catalog.writer.save(c, user, draft, catalog.clock.instant()).id());
            }
            return new ImportResult(ids, preview);
        });
    }
    private ImportPreview preview(Connection c, String user, ImportCommand command) throws SQLException {
        String shop = catalog.store.ownerShop(c, user);
        var names = new HashSet<String>();
        try (var s = prepare(c, "SELECT p.productName,m.isDeleted FROM tblProduct p "
                + "LEFT JOIN tblProductCatalog m ON p.productId=m.productId WHERE p.shopId=?", shop);
             var r = s.executeQuery()) {
            while (r.next()) if (!r.getBoolean(2)) names.add(text(r.getString(1)));
        }
        return validator.validate(command.rows(), names);
    }
}
