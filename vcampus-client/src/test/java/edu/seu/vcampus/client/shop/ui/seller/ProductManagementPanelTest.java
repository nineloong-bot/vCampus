package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductManagementPanelTest {
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
}
