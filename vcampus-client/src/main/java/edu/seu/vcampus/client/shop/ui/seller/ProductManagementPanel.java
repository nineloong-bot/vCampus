package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Seller product aggregate list and create/status actions. */
public final class ProductManagementPanel extends JPanel {
    private final SellerShopClientPort port;
    private final Runnable sessionExpired;
    private final LatestRequest requests = new LatestRequest();
    private final LatestRequest detailRequests = new LatestRequest();
    private static DefaultTableModel model() { return new DefaultTableModel(new Object[]{
            "商品名", "状态", "商品种类数", "最低价", "总库存", "预留库存", "销量"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    }; }
    private final DefaultTableModel activeModel = model(), inactiveModel = model();
    private final JTable activeTable = named(new JTable(activeModel), "seller.products.active-table");
    private final JTable inactiveTable = named(new JTable(inactiveModel), "seller.products.inactive-table");
    private final ProductEditorDialogPort dialogs;
    private final JLabel status = named(new JLabel(), "seller.products.status");
    private final JButton create;
    private final JButton update;
    private final JButton toggle;
    private final List<ProductManagementSummary> activeRows = new ArrayList<>();
    private final List<ProductManagementSummary> inactiveRows = new ArrayList<>();
    private ProductView selectedProduct;
    private String shopCategory = "文具";
    private boolean writable = true;
    private boolean disposed;

    public ProductManagementPanel(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired) {
        this(port, uiKit, sessionExpired, new SwingProductEditorDialogs(uiKit));
    }

    ProductManagementPanel(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired, ProductEditorDialogPort dialogs) {
        super(new BorderLayout(8, 8));
        this.port = Objects.requireNonNull(port, "port");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        installSelection(activeTable, inactiveTable);
        installSelection(inactiveTable, activeTable);
        create = uiKit.primaryButton("seller.products.create", "创建商品");
        update = uiKit.primaryButton("seller.products.update", "修改商品信息");
        toggle = uiKit.secondaryButton("seller.products.status-action", "上架/下架");
        update.setEnabled(false); toggle.setEnabled(false);
        create.addActionListener(event -> create()); update.addActionListener(event -> update());
        toggle.addActionListener(event -> toggle());
        JPanel actions = uiKit.filterPanel("seller.products.actions", new java.awt.FlowLayout());
        actions.add(create); actions.add(update); actions.add(toggle); actions.add(status);
        JPanel columns = uiKit.filterPanel("seller.products.columns", new java.awt.GridLayout(1, 2, 12, 0));
        columns.add(titled("上架商品", activeTable)); columns.add(titled("下架商品（含草稿）", inactiveTable));
        add(columns, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    public void load() {
        if (disposed) return;
        long request = requests.begin();
        port.searchOwnedProducts(new ProductManagementQuery(null, null, null, 0, 50))
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!requests.accepts(request)) return;
                    if (failure != null) { fail(failure); return; }
                    activeTable.clearSelection(); inactiveTable.clearSelection();
                    selectedProduct = null; update.setEnabled(false); toggle.setEnabled(false);
                    activeRows.clear(); inactiveRows.clear(); activeModel.setRowCount(0); inactiveModel.setRowCount(0);
                    for (ProductManagementSummary value : page.items()) {
                        List<ProductManagementSummary> targetRows = value.status() == ProductStatus.ACTIVE ? activeRows : inactiveRows;
                        DefaultTableModel targetModel = value.status() == ProductStatus.ACTIVE ? activeModel : inactiveModel;
                        targetRows.add(value); targetModel.addRow(new Object[]{ value.productName(), statusText(value.status()),
                                value.skuCount(), value.minimumPrice(), value.totalStock(), value.reservedStock(), value.salesCount()});
                    }
                }));
    }

    public void setShop(ShopView shop) {
        writable = shop.status() == ShopStatus.ACTIVE;
        shopCategory = shop.category();
        selectedProduct = null;
        create.setEnabled(writable); update.setEnabled(false);
        toggle.setEnabled(false);
    }
    public void disposePage() {
        disposed = true; requests.dispose(); detailRequests.dispose();
    }

    private void installSelection(JTable selectedTable, JTable otherTable) {
        selectedTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || selectedTable.getSelectedRow() < 0) return;
            otherTable.clearSelection(); selectProduct();
        });
    }

    private void selectProduct() {
        ProductManagementSummary summary = selectedSummary();
        if (summary == null) { selectedProduct = null; update.setEnabled(false); toggle.setEnabled(false); return; }
        String productId = summary.productId();
        toggle.setText(actionText(summary.status())); toggle.setEnabled(writable);
        selectedProduct = null; update.setEnabled(false);
        long request = detailRequests.begin();
        port.getOwnedProduct(productId).whenComplete((product, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!detailRequests.accepts(request) || !isSelected(productId)) return;
                    if (failure != null) { fail(failure); return; }
                    selectedProduct = product;
                    update.setEnabled(writable);
                }));
    }

    private boolean isSelected(String productId) {
        ProductManagementSummary selected = selectedSummary();
        return selected != null && selected.productId().equals(productId);
    }

    private void create() {
        if (!writable) return;
        try {
            dialogs.create(this, shopCategory).ifPresent(command ->
                    port.createOwnedProduct(command).whenComplete((ignored, failure) ->
                            SwingUtilities.invokeLater(() -> {
                                if (failure != null) fail(failure); else load();
                            })));
        } catch (RuntimeException failure) {
            status.setText(ShopUiErrors.message("COMMON_VALIDATION_FAILED"));
        }
    }
    private void update() {
        if (!writable || selectedProduct == null) return;
        try {
            dialogs.update(this, selectedProduct).ifPresent(command ->
                    port.updateOwnedProduct(command).whenComplete((ignored, failure) ->
                            SwingUtilities.invokeLater(() -> {
                                if (failure != null) fail(failure); else load();
                            })));
        } catch (RuntimeException failure) {
            status.setText(ShopUiErrors.message("COMMON_VALIDATION_FAILED"));
        }
    }
    private void toggle() {
        ProductManagementSummary value = selectedSummary();
        if (!writable || value == null) return;
        ProductStatus target = value.status() == ProductStatus.ACTIVE ? ProductStatus.INACTIVE
                : value.status() == ProductStatus.DRAFT ? ProductStatus.INACTIVE : ProductStatus.ACTIVE;
        port.changeOwnedProductStatus(new ChangeProductStatusCommand(value.productId(), target,
                value.rowVersion())).whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
                    if (failure != null) fail(failure); else load();
                }));
    }
    private void fail(Throwable failure) {
        String code = ShopUiErrors.code(failure); status.setText(ShopUiErrors.message(code));
        if (ShopUiErrors.sessionExpired(code)) sessionExpired.run();
    }
    private ProductManagementSummary selectedSummary() {
        int active = activeTable.getSelectedRow();
        if (active >= 0 && active < activeRows.size()) return activeRows.get(active);
        int inactive = inactiveTable.getSelectedRow();
        return inactive >= 0 && inactive < inactiveRows.size() ? inactiveRows.get(inactive) : null;
    }
    private static String actionText(ProductStatus status) { return switch (status) {
        case ACTIVE -> "下架"; case INACTIVE -> "上架"; case DRAFT -> "完成商品编辑"; }; }
    private static String statusText(ProductStatus status) { return switch (status) {
        case ACTIVE -> "已上架"; case INACTIVE -> "已下架"; case DRAFT -> "草稿"; }; }
    private static JPanel titled(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout()); panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(table)); return panel;
    }
    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name); return component;
    }
}
