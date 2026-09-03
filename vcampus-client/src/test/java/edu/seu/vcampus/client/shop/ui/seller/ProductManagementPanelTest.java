package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.SharedShopUiKitAdapter;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductManagementPanelTest {
    @Test
    void sharedThemeUsesCompactManagementTablesAndEditorControls() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        ProductManagementPanel management = ShopSwingTestSupport.onEdt(() ->
                new ProductManagementPanel(port, new SharedShopUiKitAdapter(), () -> { }));
        ProductEditorPanel editor = ShopSwingTestSupport.onEdt(() ->
                new ProductEditorPanel(new SharedShopUiKitAdapter()));

        JTable active = ShopSwingTestSupport.component(management,
                "seller.products.active-table", JTable.class);
        JTable inactive = ShopSwingTestSupport.component(management,
                "seller.products.inactive-table", JTable.class);
        JTable varieties = ShopSwingTestSupport.component(editor,
                "seller.editor.skus", JTable.class);

        assertThat(active.getRowHeight()).isEqualTo(34);
        assertThat(inactive.getRowHeight()).isEqualTo(34);
        assertThat(varieties.getRowHeight()).isEqualTo(34);
        assertThat(active.getShowVerticalLines()).isFalse();
        assertThat(editor.getBackground()).isEqualTo(UiColors.BACKGROUND_PAGE);
    }

    @Test
    void productListUsesFullMainAreaWithoutPersistentEditorSplit() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        ProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ProductManagementPanel(port, new DefaultShopUiKit(), () -> { }));

        assertThat(findType(panel, JSplitPane.class)).isNull();
        assertThat(findNamed(panel, "seller.editor.name")).isNull();
        assertThat(ShopSwingTestSupport.component(panel, "seller.products.active-table", JTable.class)).isNotNull();
        assertThat(ShopSwingTestSupport.component(panel, "seller.products.inactive-table", JTable.class)).isNotNull();
    }

    @Test
    void createButtonUsesProductDialogAndSubmitsConfirmedCommand() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        ProductEditorDialogPort dialogs = mock(ProductEditorDialogPort.class);
        CreateProductCommand command = new CreateProductCommand("签字笔", "文具", "说明", null,
                List.of(new CreateSkuCommand("黑色", new BigDecimal("2.50"), 10, true)));
        ProductView created = new ProductView("product-1", "签字笔", "文具", "说明", null,
                ProductStatus.DRAFT, 0, 1, List.of());
        when(dialogs.create(any(), eq("文具"))).thenReturn(Optional.of(command));
        when(port.createOwnedProduct(command)).thenReturn(CompletableFuture.completedFuture(created));
        when(port.searchOwnedProducts(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 0, 50, 0)));
        ProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ProductManagementPanel(port, new DefaultShopUiKit(), () -> { }, dialogs));
        ShopSwingTestSupport.onEdt(() -> panel.setShop(activeShop()));

        ShopSwingTestSupport.onEdt(() -> ShopSwingTestSupport.component(panel,
                "seller.products.create", JButton.class).doClick());
        ShopSwingTestSupport.flushEdt();

        verify(dialogs).create(panel, "文具");
        verify(port).createOwnedProduct(command);
    }

    @Test
    void updateButtonUsesProductDialogForSelectedAggregate() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        ProductEditorDialogPort dialogs = mock(ProductEditorDialogPort.class);
        ProductManagementSummary summary = new ProductManagementSummary("product-1", "签字笔",
                ProductStatus.DRAFT, 1, new BigDecimal("2.50"), 10, 0, 0, 7);
        ProductView detail = new ProductView("product-1", "签字笔", "文具", "说明", null,
                ProductStatus.DRAFT, 0, 7, List.of());
        UpdateProductCommand command = new UpdateProductCommand("product-1", "签字笔", "文具",
                "更新说明", null, List.of(), 7);
        when(port.searchOwnedProducts(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(summary), 0, 50, 1)));
        when(port.getOwnedProduct("product-1")).thenReturn(CompletableFuture.completedFuture(detail));
        when(dialogs.update(any(), eq(detail))).thenReturn(Optional.of(command));
        when(port.updateOwnedProduct(command)).thenReturn(CompletableFuture.completedFuture(detail));
        ProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ProductManagementPanel(port, new DefaultShopUiKit(), () -> { }, dialogs));
        ShopSwingTestSupport.onEdt(() -> panel.setShop(activeShop()));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        JTable products = ShopSwingTestSupport.component(panel, "seller.products.inactive-table", JTable.class);
        ShopSwingTestSupport.onEdt(() -> products.setRowSelectionInterval(0, 0));
        ShopSwingTestSupport.flushEdt();
        ShopSwingTestSupport.onEdt(() -> ShopSwingTestSupport.component(panel,
                "seller.products.update", JButton.class).doClick());
        ShopSwingTestSupport.flushEdt();

        verify(dialogs).update(panel, detail);
        verify(port).updateOwnedProduct(command);
    }

    @Test
    void sellerNeverEditsSkuIdVersionOrRawCoverUrl() throws Exception {
        ProductEditorPanel editor = ShopSwingTestSupport.onEdt(() ->
                new ProductEditorPanel(new DefaultShopUiKit()));

        assertThat(findNamed(editor, "seller.editor.cover")).isNull();
        JTable table = ShopSwingTestSupport.component(editor, "seller.editor.skus", JTable.class);
        assertThat(java.util.stream.IntStream.range(0, table.getColumnCount())
                .mapToObj(table::getColumnName).toList())
                .containsExactly("商品种类名称", "单价", "库存", "状态", "操作");
        assertThat(table.getModel().isCellEditable(0, 0)).isFalse();
    }

    @Test
    void addProductVarietyActionIsVisibleBeforeTheVarietyTable() throws Exception {
        ProductEditorPanel editor = ShopSwingTestSupport.onEdt(() ->
                new ProductEditorPanel(new DefaultShopUiKit()));

        ShopSwingTestSupport.onEdt(() -> {
            editor.setSize(900, 650);
            layoutTree(editor);
        });
        JButton add = ShopSwingTestSupport.component(editor,
                "seller.editor.add-sku", JButton.class);
        JTable varieties = ShopSwingTestSupport.component(editor,
                "seller.editor.skus", JTable.class);

        int addY = SwingUtilities.convertPoint(add.getParent(), add.getLocation(), editor).y;
        int tableY = SwingUtilities.convertPoint(
                varieties.getParent(), varieties.getLocation(), editor).y;
        assertThat(addY).isLessThan(tableY);
    }

    @Test
    void rendersOneRowPerProductWithSkuInventoryAndSalesAggregates() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        when(port.searchOwnedProducts(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(new ProductManagementSummary("product-1", "签字笔",
                        ProductStatus.ACTIVE, 2, new BigDecimal("2.50"), 30, 4, 99, 7)),
                        0, 50, 1)));
        ProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ProductManagementPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        JTable table = ShopSwingTestSupport.component(panel, "seller.products.active-table", JTable.class);

        assertThat(table.getColumnName(2)).isEqualTo("商品种类数");
        assertThat(ShopSwingTestSupport.component(panel,
                "seller.products.update", JButton.class).getText()).isEqualTo("修改商品信息");
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("签字笔");
        assertThat(table.getValueAt(0, 2)).isEqualTo(2L);
        assertThat(table.getValueAt(0, 5)).isEqualTo(4L);
        assertThat(table.getValueAt(0, 6)).isEqualTo(99L);
    }

    @Test
    void partitionsActiveFromInactiveAndDraftAndUsesStateSpecificActions() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        ProductManagementSummary active = summary("active", ProductStatus.ACTIVE, 1);
        ProductManagementSummary inactive = summary("inactive", ProductStatus.INACTIVE, 2);
        ProductManagementSummary draft = summary("draft", ProductStatus.DRAFT, 3);
        when(port.searchOwnedProducts(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(active, inactive, draft), 0, 50, 3)));
        when(port.getOwnedProduct(any())).thenAnswer(invocation -> CompletableFuture.completedFuture(
                new ProductView(invocation.getArgument(0), "商品", "文具", "说明", null,
                        ProductStatus.DRAFT, 0, 3, List.of())));
        when(port.changeOwnedProductStatus(any())).thenReturn(CompletableFuture.completedFuture(null));
        ProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ProductManagementPanel(port, new DefaultShopUiKit(), () -> { }));
        ShopSwingTestSupport.onEdt(() -> panel.setShop(activeShop()));
        ShopSwingTestSupport.onEdt(panel::load); ShopSwingTestSupport.flushEdt();
        JTable activeTable = ShopSwingTestSupport.component(panel,
                "seller.products.active-table", JTable.class);
        JTable inactiveTable = ShopSwingTestSupport.component(panel,
                "seller.products.inactive-table", JTable.class);
        JButton action = ShopSwingTestSupport.component(panel,
                "seller.products.status-action", JButton.class);

        assertThat(activeTable.getRowCount()).isEqualTo(1);
        assertThat(inactiveTable.getRowCount()).isEqualTo(2);
        assertThat(inactiveTable.getValueAt(1, 1)).isEqualTo("草稿");
        ShopSwingTestSupport.onEdt(() -> inactiveTable.setRowSelectionInterval(1, 1));
        ShopSwingTestSupport.flushEdt();
        assertThat(activeTable.getSelectedRow()).isEqualTo(-1);
        assertThat(action.getText()).isEqualTo("完成商品编辑");
        ShopSwingTestSupport.onEdt(() -> action.doClick());
        ShopSwingTestSupport.flushEdt();
        verify(port).changeOwnedProductStatus(new ChangeProductStatusCommand(
                "draft", ProductStatus.INACTIVE, 3));
    }

    @Test
    void productEditorRetainsStableSkuIdentityForDialogUpdate() throws Exception {
        ProductView detail = new ProductView("product-1", "签字笔", "文具", "说明", null,
                ProductStatus.DRAFT, 0, 7, List.of(new ProductSkuView("sku-1", "黑色",
                        new BigDecimal("2.50"), 6, 10, 4, true, 3)));
        ProductEditorPanel editor = ShopSwingTestSupport.onEdt(() ->
                new ProductEditorPanel(new DefaultShopUiKit()));

        ShopSwingTestSupport.onEdt(() -> editor.load(detail));

        assertThat(ShopSwingTestSupport.component(editor, "seller.editor.name",
                JTextField.class).getText()).isEqualTo("签字笔");
        JTable skus = ShopSwingTestSupport.component(editor, "seller.editor.skus", JTable.class);
        assertThat(skus.getValueAt(0, 0)).isEqualTo("黑色");
        assertThat(skus.getValueAt(0, 2)).isEqualTo(10L);
        assertThat(skus.getColumnCount()).isEqualTo(5);

        assertThat(ShopSwingTestSupport.onEdt(editor::updateCommand)).isEqualTo(
                new UpdateProductCommand("product-1", "签字笔", "文具",
                "说明", null, List.of(new UpsertSkuCommand("sku-1", "黑色",
                        new BigDecimal("2.50"), 10, true, 3)), 7));
    }

    private static Component findNamed(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component match = findNamed(nested, name);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static <T extends Component> T findType(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T match = findType(nested, type);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    private static ShopView activeShop() {
        return new ShopView("shop-1", "owner-1", "店铺", "简介", "文具", "contact",
                ShopStatus.ACTIVE, null, null, (Instant) null, 1);
    }

    private static ProductManagementSummary summary(String id, ProductStatus status, long version) {
        return new ProductManagementSummary(id, id, status, 1, new BigDecimal("2.50"),
                10, 0, 0, version);
    }
}
