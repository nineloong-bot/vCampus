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
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{
            "商品名", "状态", "SKU 数", "最低价", "总库存", "预留库存", "销量"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = named(new JTable(model), "seller.products.table");
    private final ProductEditorPanel editor;
    private final JLabel status = named(new JLabel(), "seller.products.status");
    private final JButton create;
    private final JButton toggle;
    private final List<ProductManagementSummary> rows = new ArrayList<>();
    private boolean writable = true;
    private boolean disposed;

    public ProductManagementPanel(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.port = Objects.requireNonNull(port, "port");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        editor = new ProductEditorPanel(uiKit);
        create = uiKit.primaryButton("seller.products.create", "创建商品");
        toggle = uiKit.secondaryButton("seller.products.toggle", "切换上架状态");
        create.addActionListener(event -> create()); toggle.addActionListener(event -> toggle());
        JPanel actions = uiKit.filterPanel("seller.products.actions", new java.awt.FlowLayout());
        actions.add(create); actions.add(toggle); actions.add(status);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), editor);
        split.setResizeWeight(0.55); add(split, BorderLayout.CENTER); add(actions, BorderLayout.SOUTH);
    }

    public void load() {
        if (disposed) return;
        long request = requests.begin();
        port.searchOwnedProducts(new ProductManagementQuery(null, null, null, 0, 50))
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!requests.accepts(request)) return;
                    if (failure != null) { fail(failure); return; }
                    rows.clear(); rows.addAll(page.items()); model.setRowCount(0);
                    for (ProductManagementSummary value : rows) model.addRow(new Object[]{
                            value.productName(), value.status().name(), value.skuCount(),
                            value.minimumPrice(), value.totalStock(), value.reservedStock(),
                            value.salesCount()});
                }));
    }

    public void setShop(ShopView shop) {
        writable = shop.status() == ShopStatus.ACTIVE; editor.clear(shop.category());
        editor.setWritable(writable); create.setEnabled(writable); toggle.setEnabled(writable);
    }
    public void disposePage() { disposed = true; requests.dispose(); }

    private void create() {
        if (!writable) return;
        try {
            port.createOwnedProduct(editor.createCommand()).whenComplete((ignored, failure) ->
                    SwingUtilities.invokeLater(() -> { if (failure != null) fail(failure); else load(); }));
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
