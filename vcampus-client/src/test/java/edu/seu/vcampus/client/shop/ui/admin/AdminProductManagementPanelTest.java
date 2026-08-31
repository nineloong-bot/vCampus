package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
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
    void selectingProductLoadsSelectedShopDetailAndUpdatesWithExplicitShopId() throws Exception {
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
        when(port.updateProduct(any())).thenReturn(CompletableFuture.completedFuture(detail));
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

        verify(port).getProduct(new AdminProductRef("shop-1", "product-1"));
        assertThat(ShopSwingTestSupport.component(panel, "seller.editor.name",
                JTextField.class).getText()).isEqualTo("签字笔");
        JTable skus = ShopSwingTestSupport.component(panel, "seller.editor.skus", JTable.class);
        assertThat(skus.getValueAt(0, 0)).isEqualTo("sku-1");
        assertThat(skus.getValueAt(0, 3)).isEqualTo(10L);

        JButton update = ShopSwingTestSupport.component(panel, "admin.products.update", JButton.class);
        ShopSwingTestSupport.onEdt(() -> { update.doClick(); });
        ShopSwingTestSupport.flushEdt();
        verify(port).updateProduct(new AdminUpdateProductCommand("shop-1",
                new UpdateProductCommand("product-1", "签字笔", "文具", "说明", null,
                        List.of(new UpsertSkuCommand("sku-1", "黑色", new BigDecimal("2.50"),
                                10, true, 3)), 7)));
    }
}
