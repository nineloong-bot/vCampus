package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductManagementPanelTest {
    @Test
    void sellerNeverEditsSkuIdVersionOrRawCoverUrl() throws Exception {
        ProductEditorPanel editor = ShopSwingTestSupport.onEdt(() ->
                new ProductEditorPanel(new DefaultShopUiKit()));

        assertThat(findNamed(editor, "seller.editor.cover")).isNull();
        JTable table = ShopSwingTestSupport.component(editor, "seller.editor.skus", JTable.class);
        assertThat(java.util.stream.IntStream.range(0, table.getColumnCount())
                .mapToObj(table::getColumnName).toList())
                .containsExactly("规格名称", "单价", "库存", "状态", "操作");
        assertThat(table.getModel().isCellEditable(0, 0)).isFalse();
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
        JTable table = ShopSwingTestSupport.component(panel, "seller.products.table", JTable.class);

        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("签字笔");
        assertThat(table.getValueAt(0, 2)).isEqualTo(2L);
        assertThat(table.getValueAt(0, 5)).isEqualTo(4L);
        assertThat(table.getValueAt(0, 6)).isEqualTo(99L);
    }

    @Test
    void selectingExistingProductLoadsStableSkuIdentityAndUpdatesIt() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        ProductManagementSummary summary = new ProductManagementSummary("product-1", "签字笔",
                ProductStatus.DRAFT, 1, new BigDecimal("2.50"), 10, 4, 0, 7);
        ProductView detail = new ProductView("product-1", "签字笔", "文具", "说明", null,
                ProductStatus.DRAFT, 0, 7, List.of(new ProductSkuView("sku-1", "黑色",
                        new BigDecimal("2.50"), 6, 10, 4, true, 3)));
        when(port.searchOwnedProducts(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(summary), 0, 50, 1)));
        when(port.getOwnedProduct("product-1")).thenReturn(
                CompletableFuture.completedFuture(detail));
        when(port.updateOwnedProduct(any())).thenReturn(CompletableFuture.completedFuture(detail));
        ProductManagementPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ProductManagementPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        JTable products = ShopSwingTestSupport.component(panel, "seller.products.table", JTable.class);
        ShopSwingTestSupport.onEdt(() -> products.setRowSelectionInterval(0, 0));
        ShopSwingTestSupport.flushEdt();

        verify(port).getOwnedProduct("product-1");
        assertThat(ShopSwingTestSupport.component(panel, "seller.editor.name",
                JTextField.class).getText()).isEqualTo("签字笔");
        JTable skus = ShopSwingTestSupport.component(panel, "seller.editor.skus", JTable.class);
        assertThat(skus.getValueAt(0, 0)).isEqualTo("黑色");
        assertThat(skus.getValueAt(0, 2)).isEqualTo(10L);
        assertThat(skus.getColumnCount()).isEqualTo(5);

        JButton update = ShopSwingTestSupport.component(panel, "seller.products.update", JButton.class);
        ShopSwingTestSupport.onEdt(() -> { update.doClick(); });
        ShopSwingTestSupport.flushEdt();
        verify(port).updateOwnedProduct(new UpdateProductCommand("product-1", "签字笔", "文具",
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
}
