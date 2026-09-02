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
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{
            "商品名", "状态", "商品种类数", "最低价", "总库存", "预留库存", "销量"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = named(new JTable(model), "seller.products.table");
    private final ProductEditorDialogPort dialogs;
    private final JLabel status = named(new JLabel(), "seller.products.status");
    private final JButton create;
    private final JButton update;
    private final JButton toggle;
    private final List<ProductManagementSummary> rows = new ArrayList<>();
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
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) selectProduct();
        });
        create = uiKit.primaryButton("seller.products.create", "创建商品");
        update = uiKit.primaryButton("seller.products.update", "修改商品信息");
        toggle = uiKit.secondaryButton("seller.products.toggle", "切换上架状态");
        update.setEnabled(false);
        create.addActionListener(event -> create()); update.addActionListener(event -> update());
        toggle.addActionListener(event -> toggle());
        JPanel actions = uiKit.filterPanel("seller.products.actions", new java.awt.FlowLayout());
        actions.add(create); actions.add(update); actions.add(toggle); actions.add(status);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    public void load() {
        if (disposed) return;
        long request = requests.begin();
        port.searchOwnedProducts(new ProductManagementQuery(null, null, null, 0, 50))
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!requests.accepts(request)) return;
                    if (failure != null) { fail(failure); return; }
                    table.clearSelection(); selectedProduct = null; update.setEnabled(false);
                    rows.clear(); rows.addAll(page.items()); model.setRowCount(0);
                    for (ProductManagementSummary value : rows) model.addRow(new Object[]{
                            value.productName(), value.status().name(), value.skuCount(),
                            value.minimumPrice(), value.totalStock(), value.reservedStock(),
                            value.salesCount()});
                }));
    }

    public void setShop(ShopView shop) {
        writable = shop.status() == ShopStatus.ACTIVE;
        shopCategory = shop.category();
        selectedProduct = null;
        create.setEnabled(writable); update.setEnabled(false);
        toggle.setEnabled(writable);
    }
    public void disposePage() {
        disposed = true; requests.dispose(); detailRequests.dispose();
    }

    private void selectProduct() {
        int selected = table.getSelectedRow();
        if (selected < 0 || selected >= rows.size()) return;
        String productId = rows.get(selected).productId();
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
        int selected = table.getSelectedRow();
        return selected >= 0 && selected < rows.size()
                && rows.get(selected).productId().equals(productId);
    }

    private void create() {
        if (!writable) return;
        try {
            dialogs.create(this, shopCategory).ifPresent(command ->
                    port.createOwnedProduct(command).whenComplete((ignored, failure) ->
                            SwingUtilities.invokeLater(() -> {
                                if (failure != null) fail(failure); else load();
                            })));
        } catch (RuntimeException failure) { status.setText("COMMON_VALIDATION_FAILED"); }
    }
    private void update() {
        if (!writable || selectedProduct == null) return;
        try {
            dialogs.update(this, selectedProduct).ifPresent(command ->
                    port.updateOwnedProduct(command).whenComplete((ignored, failure) ->
                            SwingUtilities.invokeLater(() -> {
                                if (failure != null) fail(failure); else load();
                            })));
        } catch (RuntimeException failure) { status.setText("COMMON_VALIDATION_FAILED"); }
    }
    private void toggle() {
        int selected = table.getSelectedRow();
        if (!writable || selected < 0 || selected >= rows.size()) return;
        ProductManagementSummary value = rows.get(selected);
        ProductStatus target = value.status() == ProductStatus.ACTIVE
                ? ProductStatus.INACTIVE : ProductStatus.ACTIVE;
        port.changeOwnedProductStatus(new ChangeProductStatusCommand(value.productId(), target,
                value.rowVersion())).whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
                    if (failure != null) fail(failure); else load();
                }));
    }
    private void fail(Throwable failure) {
        String code = ShopUiErrors.code(failure); status.setText(code);
        if (ShopUiErrors.sessionExpired(code)) sessionExpired.run();
    }
    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name); return component;
    }
}
