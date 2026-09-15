import io.github.spannm.jackcess.Column;
import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.Index;
import io.github.spannm.jackcess.IndexBuilder;
import io.github.spannm.jackcess.Row;
import io.github.spannm.jackcess.Table;
import io.github.spannm.jackcess.TableBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Opt-in, source-authoritative commerce transplant onto a new copy of main. */
public final class MainCommerceMigration {
    static final Set<String> OWNED = Set.of(
            "tblSellerApplication", "tblShop", "tblProduct", "tblProductSku", "tblCart",
            "tblCartItem", "tblOrderGroup", "tblOrder", "tblOrderItem", "tblPayment",
            "tblPaymentAttempt", "tblInventoryReservation", "tblWalletAccount",
            "tblWalletOperation", "tblWalletEntry", "tblWalletEscrow", "tblProductCatalog",
            "tblSkuDraftFields", "tblCatalogReceipt", "tblShopOrderState", "tblShopOrderLineState",
            "tblShopOrderReceipt", "tblShopInventoryMovement", "tblShopOrderEvent",
            "tblShopGovApplication", "tblShopQualification", "tblShopProductRestriction",
            "tblShopGovCase", "tblShopGovAudit", "tblShopGovReceipt");

    /** Arguments: --source-commerce main-snapshot source-snapshot NEW-output-path. */
    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !args[0].equals("--source-commerce")) {
            throw new IllegalArgumentException("Expected --source-commerce main source NEW-output");
        }
        Path main = Path.of(args[1]).toRealPath();
        Path source = Path.of(args[2]).toRealPath();
        Path output = Path.of(args[3]).toAbsolutePath().normalize();
        if (Files.exists(output)) {
            throw new java.nio.file.FileAlreadyExistsException(output.toString());
        }
        try (Database m = CommerceDataChecks.open(main, true);
                Database s = CommerceDataChecks.open(source, true)) {
            CommerceDataChecks.accounts(m, s);
            CommerceIntegrity.validate(s);
            // Do not silently erase main-only history from an older source snapshot.
            CommerceDataChecks.mainKeysRetained(m, s);
        }
        Files.copy(main, output);
        try (Database target = CommerceDataChecks.open(output, false);
                Database src = CommerceDataChecks.open(source, true)) {
            target.setEnforceForeignKeys(false);
            for (String name : OWNED.stream().sorted().toList()) {
                Table from = src.getTable(name);
                if (from == null) {
                    throw new IllegalStateException("Missing required source table " + name);
                }
                Table to = target.getTable(name);
                if (to == null) {
                    to = cloneDefinition(from, target);
                } else if (!CommerceDataChecks.columns(from).equals(CommerceDataChecks.columns(to))) {
                    throw new IllegalStateException("Schema mismatch " + name);
                }
                List<Row> old = new ArrayList<>();
                to.forEach(old::add);
                for (Row row : old) {
                    to.deleteRow(row);
                }
                for (Row row : from) {
                    to.addRowFromMap(row);
                }
            }
            cloneRelationships(src, target);
            target.setEnforceForeignKeys(true);
            target.flush();
        }
        String manifest = CommerceDataChecks.verify(main, source, output);
        Files.writeString(Path.of(output + ".manifest.tsv"), manifest);
        System.out.println("Verified candidate: " + output);
    }

    private static Table cloneDefinition(Table source, Database target) throws Exception {
        TableBuilder builder = new TableBuilder(source.getName());
        for (Column column : source.getColumns()) {
            builder.addColumn(new ColumnBuilder(column.getName()).withFromColumn(column));
        }
        for (Index index : source.getIndexes()) {
            if (index.isForeignKey() && !index.isPrimaryKey()) {
                continue;
            }
            IndexBuilder copy = new IndexBuilder(index.getName());
            for (Index.Column column : index.getColumns()) {
                copy.withColumns(column.isAscending(), column.getName());
            }
            if (index.isPrimaryKey()) {
                copy.withPrimaryKey();
            } else if (index.isUnique()) {
                copy.withUnique();
            }
            if (index.isRequired()) {
                copy.withRequired();
            }
            builder.addIndex(copy);
        }
        return builder.toTable(target);
    }
    private static void cloneRelationships(Database source, Database target) throws Exception {
        Set<String> existing = new java.util.HashSet<>();
        for (var relationship : target.getRelationships()) {
            existing.add(relationship.getName());
        }
        for (var relationship : source.getRelationships()) {
            String from = relationship.getFromTable().getName();
            String to = relationship.getToTable().getName();
            if (existing.contains(relationship.getName()) || !OWNED.contains(to)) {
                continue;
            }
            var builder = new io.github.spannm.jackcess.RelationshipBuilder(from, to)
                    .withName(relationship.getName()).withJoinType(relationship.getJoinType());
            for (int i = 0; i < relationship.getFromColumns().size(); i++) {
                builder.addColumns(relationship.getFromColumns().get(i).getName(),
                        relationship.getToColumns().get(i).getName());
            }
            if (relationship.hasReferentialIntegrity()) {
                builder.withReferentialIntegrity();
            }
            if (relationship.cascadeUpdates()) {
                builder.withCascadeUpdates();
            }
            if (relationship.cascadeDeletes()) {
                builder.withCascadeDeletes();
            }
            if (relationship.cascadeNullOnDelete()) {
                builder.withCascadeNullOnDelete();
            }
            builder.toRelationship(target);
        }
    }
}
