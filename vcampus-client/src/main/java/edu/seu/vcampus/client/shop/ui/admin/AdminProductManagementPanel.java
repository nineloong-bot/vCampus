package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.seller.ProductEditorDialogPort;
import edu.seu.vcampus.client.shop.ui.seller.SwingProductEditorDialogs;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shop-selection-fenced administrator product workspace. */
public final class AdminProductManagementPanel extends JPanel {
    private final AdminShopClientPort port;
    private final Runnable sessionExpired;
    private final LatestRequest shopRequests = new LatestRequest();
    private final LatestRequest productRequests = new LatestRequest();
    private final LatestRequest detailRequests = new LatestRequest();
    private final DefaultTableModel shopModel = readonly(
            new Object[]{"店铺 ID", "店主", "店铺名称", "类别", "状态"});
    private final JTable shops = named(new JTable(shopModel), "admin.products.shops");
    private final DefaultTableModel productModel = readonly(
            new Object[]{"商品名", "状态", "商品种类数", "最低价", "库存", "预留", "销量"});
    private final JTable products = named(new JTable(productModel), "admin.products.table");
    private final JTextField category = named(new JTextField(), "admin.products.category");
    private final JLabel status = named(new JLabel(), "admin.products.status");
    private final ProductEditorDialogPort dialogs;
    private final JButton create;
    private final JButton update;
    private final JButton toggle;
    private final List<ShopAdminSummary> shopRows = new ArrayList<>();
    private final List<ProductManagementSummary> productRows = new ArrayList<>();
    private ShopAdminSummary selectedShop;
    private ProductView selectedProduct;
    private boolean disposed;

    public AdminProductManagementPanel(AdminShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired) {
        this(port, uiKit, sessionExpired, new SwingProductEditorDialogs(uiKit));
    }

    AdminProductManagementPanel(AdminShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired, ProductEditorDialogPort dialogs) {
        super(new BorderLayout(8, 8));
        this.port = Objects.requireNonNull(port, "port");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        category.setEditable(false);
        shops.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) selectShop();
        });
        products.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) selectProduct();
        });
        create = uiKit.primaryButton("admin.products.create", "为所选店铺创建商品");
        update = uiKit.primaryButton("admin.products.update", "修改商品信息");
        toggle = uiKit.secondaryButton("admin.products.toggle", "切换上架状态");
        create.setEnabled(false); update.setEnabled(false); toggle.setEnabled(false);
        create.addActionListener(event -> create()); update.addActionListener(event -> update());
        toggle.addActionListener(event -> toggle());
        JPanel selected = uiKit.filterPanel("admin.products.selected", new BorderLayout(4, 4));
        selected.add(new JLabel("所选店铺类别"), BorderLayout.WEST);
        selected.add(category, BorderLayout.CENTER);
        JPanel lists = uiKit.filterPanel("admin.products.lists", new GridLayout(2, 1, 0, 8));
        lists.add(new JScrollPane(shops));
        lists.add(new JScrollPane(products));
        JPanel workspace = uiKit.filterPanel("admin.products.workspace", new BorderLayout(4, 4));
        workspace.add(lists, BorderLayout.CENTER);
        workspace.add(selected, BorderLayout.SOUTH);
        JPanel actions = uiKit.filterPanel("admin.products.actions", new java.awt.FlowLayout());
        actions.add(create); actions.add(update); actions.add(toggle); actions.add(status);
        add(workspace, BorderLayout.CENTER); add(actions, BorderLayout.SOUTH);
    }

    public void load() {
        if (disposed) return;
        long request = shopRequests.begin();
        port.searchShops(new ShopAdminQuery(null, null, 0, 50)).whenComplete((page, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!shopRequests.accepts(request)) return;
                    if (failure != null) { fail(failure); return; }
                    shopRows.clear(); shopRows.addAll(page.items()); shopModel.setRowCount(0);
                    for (ShopAdminSummary value : shopRows) shopModel.addRow(new Object[]{
                            value.shopId(), value.ownerUserId(), value.shopName(), value.category(),
                            value.status().name()});
                }));
    }

    public void disposePage() {
        disposed = true; shopRequests.dispose(); productRequests.dispose(); detailRequests.dispose();
    }

    private void selectShop() {
        int selected = shops.getSelectedRow();
        if (selected < 0 || selected >= shopRows.size()) return;
        selectedShop = shopRows.get(selected); category.setText(selectedShop.category());
        selectedProduct = null;
        detailRequests.begin(); create.setEnabled(true); update.setEnabled(false);
        toggle.setEnabled(true); loadProducts(selectedShop.shopId(), null);
    }

    private void loadProducts(String shopId, String productIdToRestore) {
        long request = productRequests.begin();
        port.searchProducts(new ProductManagementQuery(shopId, null, null, 0, 50))
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!productRequests.accepts(request) || selectedShop == null
                            || !selectedShop.shopId().equals(shopId)) return;
                    if (failure != null) { fail(failure); return; }
                    products.clearSelection(); selectedProduct = null; update.setEnabled(false);
                    productRows.clear(); productRows.addAll(page.items()); productModel.setRowCount(0);
                    for (ProductManagementSummary value : productRows) productModel.addRow(new Object[]{
                            value.productName(), value.status().name(), value.skuCount(),
                            value.minimumPrice(), value.totalStock(), value.reservedStock(),
                            value.salesCount()});
                    if (productIdToRestore != null) {
                        for (int index = 0; index < productRows.size(); index++) {
                            if (productRows.get(index).productId().equals(productIdToRestore)) {
                                products.setRowSelectionInterval(index, index);
                                break;
                            }
                        }
                    }
                }));
    }

    private void selectProduct() {
        ShopAdminSummary shop = selectedShop; int selected = products.getSelectedRow();
        if (shop == null || selected < 0 || selected >= productRows.size()) return;
        String productId = productRows.get(selected).productId();
        selectedProduct = null; update.setEnabled(false);
        long request = detailRequests.begin();
        port.getProduct(new AdminProductRef(shop.shopId(), productId)).whenComplete((product, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!detailRequests.accepts(request) || !isSelected(shop.shopId(), productId)) return;
                    if (failure != null) { fail(failure); return; }
                    selectedProduct = product; update.setEnabled(true);
                }));
    }

    private boolean isSelected(String shopId, String productId) {
        int selected = products.getSelectedRow();
        return selectedShop != null && selectedShop.shopId().equals(shopId)
                && selected >= 0 && selected < productRows.size()
                && productRows.get(selected).productId().equals(productId);
    }

    private void create() {
        ShopAdminSummary shop = selectedShop; if (shop == null) return;
        try {
            dialogs.create(this, shop.category()).ifPresent(command ->
                    port.createProduct(new AdminCreateProductCommand(shop.shopId(), command))
                            .whenComplete((ignored, failure) -> finishMutation(shop.shopId(), failure)));
        } catch (RuntimeException failure) { status.setText("COMMON_VALIDATION_FAILED"); }
    }

    private void update() {
        ShopAdminSummary shop = selectedShop; ProductView product = selectedProduct;
        if (shop == null || product == null) return;
        try {
            dialogs.update(this, product).ifPresent(command ->
                    port.updateProduct(new AdminUpdateProductCommand(shop.shopId(), command))
                            .whenComplete((ignored, failure) -> finishMutation(shop.shopId(), failure)));
        } catch (RuntimeException failure) { status.setText("COMMON_VALIDATION_FAILED"); }
    }

    private void toggle() {
        ShopAdminSummary shop = selectedShop; int selected = products.getSelectedRow();
        if (shop == null || selected < 0 || selected >= productRows.size()) return;
        ProductManagementSummary product = productRows.get(selected);
        ProductStatus target = product.status() == ProductStatus.ACTIVE
                ? ProductStatus.INACTIVE : ProductStatus.ACTIVE;
        port.changeProductStatus(new AdminChangeProductStatusCommand(shop.shopId(),
                new ChangeProductStatusCommand(product.productId(), target, product.rowVersion())))
                .whenComplete((ignored, failure) -> finishMutation(shop.shopId(), failure));
    }

    private void finishMutation(String shopId, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (disposed || selectedShop == null || !selectedShop.shopId().equals(shopId)) return;
            if (failure != null) fail(failure); else loadProducts(shopId,
                    selectedProduct == null ? null : selectedProduct.productId());
        });
    }

    private void fail(Throwable failure) {
        String code = ShopUiErrors.code(failure); status.setText(code);
        if (ShopUiErrors.sessionExpired(code)) sessionExpired.run();
    }
    private static DefaultTableModel readonly(Object[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }
    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name); return component;
    }
}
