package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.SharedShopUiKitAdapter;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminProductManagementPanelTest {
    @Test
    void sharedThemeUsesCompactShopAndProductTables() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        AdminProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new AdminProductManagementPanel(port, new SharedShopUiKitAdapter(), () -> { }));

        assertThat(ShopSwingTestSupport.component(panel,
                "admin.products.shops", JTable.class).getRowHeight()).isEqualTo(34);
        assertThat(ShopSwingTestSupport.component(panel,
                "admin.products.table", JTable.class).getRowHeight()).isEqualTo(34);
    }

    @Test
    void shopAndProductListsUseTheFullWidthWithoutAPersistentEditorSplit() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        AdminProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new AdminProductManagementPanel(port, new DefaultShopUiKit(), () -> { }));

        assertThat(findType(panel, JSplitPane.class)).isNull();
        assertThat(findNamed(panel, "seller.editor.name")).isNull();
        assertThat(ShopSwingTestSupport.component(panel,
                "admin.products.shops", JTable.class)).isNotNull();
        assertThat(ShopSwingTestSupport.component(panel,
                "admin.products.table", JTable.class)).isNotNull();
    }

    @Test
    void waitsForShopSelectionThenQueriesProductsWithSelectedShopId() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        when(port.searchShops(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(new ShopAdminSummary("shop-1", "owner-1", "文具店",
                        "文具", ShopStatus.ACTIVE, 3, 2)), 0, 50, 1)));
        when(port.searchProducts(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 0, 50, 0)));
        AdminProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new AdminProductManagementPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        verify(port, never()).searchProducts(any());
        JTable shops = ShopSwingTestSupport.component(panel, "admin.products.shops", JTable.class);
        assertThat(shops.getRowCount()).isEqualTo(1);

        ShopSwingTestSupport.onEdt(() -> shops.setRowSelectionInterval(0, 0));
        ShopSwingTestSupport.flushEdt();

        verify(port).searchProducts(new ProductManagementQuery("shop-1", null, null, 0, 50));
        assertThat(ShopSwingTestSupport.component(panel, "admin.products.category",
                javax.swing.JTextField.class).getText()).isEqualTo("文具");
    }

    @Test
    void selectingProductLoadsSelectedShopDetailIntoEmbeddedWorkspace() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        ShopAdminSummary shop = new ShopAdminSummary("shop-1", "owner-1", "文具店",
                "文具", ShopStatus.ACTIVE, 3, 2);
        ProductManagementSummary summary = new ProductManagementSummary("product-1", "签字笔",
                ProductStatus.DRAFT, 1, new BigDecimal("2.50"), 10, 4, 0, 7);
        ProductView detail = new ProductView("product-1", "签字笔", "文具", "说明", null,
                ProductStatus.DRAFT, 0, 7, List.of(new ProductSkuView("sku-1", "黑色",
                        new BigDecimal("2.50"), 6, 10, 4, true, 3)));
        when(port.searchShops(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(shop), 0, 50, 1)));
        when(port.searchProducts(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(summary), 0, 50, 1)));
        when(port.getProduct(new AdminProductRef("shop-1", "product-1")))
                .thenReturn(CompletableFuture.completedFuture(detail));
        AdminProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new AdminProductManagementPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        JTable shops = ShopSwingTestSupport.component(panel, "admin.products.shops", JTable.class);
        ShopSwingTestSupport.onEdt(() -> shops.setRowSelectionInterval(0, 0));
        ShopSwingTestSupport.flushEdt();
        JTable products = ShopSwingTestSupport.component(panel, "admin.products.table", JTable.class);
        ShopSwingTestSupport.onEdt(() -> products.setRowSelectionInterval(0, 0));
        ShopSwingTestSupport.flushEdt();

        JButton update = ShopSwingTestSupport.component(panel, "admin.products.update", JButton.class);
        ShopSwingTestSupport.onEdt(() -> { update.doClick(); });
        assertThat(ShopSwingTestSupport.component(panel,
                "seller.editor.name", JTextField.class).getText()).isEqualTo("签字笔");
        verify(port, never()).updateProduct(any());
    }

    @Test
    void createUsesEmbeddedWorkspaceForTheSelectedShop() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        ShopAdminSummary shop = new ShopAdminSummary("shop-1", "owner-1", "文具店",
                "文具", ShopStatus.ACTIVE, 3, 2);
        when(port.searchShops(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(shop), 0, 50, 1)));
        when(port.searchProducts(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 0, 50, 0)));
        AdminProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new AdminProductManagementPanel(port, new DefaultShopUiKit(), () -> { }));
        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        JTable shops = ShopSwingTestSupport.component(panel, "admin.products.shops", JTable.class);
        ShopSwingTestSupport.onEdt(() -> shops.setRowSelectionInterval(0, 0));
        ShopSwingTestSupport.flushEdt();

        ShopSwingTestSupport.onEdt(() -> ShopSwingTestSupport.component(panel,
                "admin.products.create", JButton.class).doClick());

        assertThat(findNamed(panel, "seller.editor.name")).isNotNull();
        verify(port, never()).createProduct(any());
    }

    private static java.awt.Component findNamed(java.awt.Container root, String name) {
        for (java.awt.Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof java.awt.Container nested) {
                java.awt.Component match = findNamed(nested, name);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static <T extends java.awt.Component> T findType(java.awt.Container root,
            Class<T> type) {
        for (java.awt.Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof java.awt.Container nested) {
                T match = findType(nested, type);
                if (match != null) return match;
            }
        }
        return null;
    }
}
