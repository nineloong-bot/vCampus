package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SellerOrdersPanelTest {
    @Test
    void expandsReturnedShopOrderSnapshots() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        SellerOrderItemView item = new SellerOrderItemView("product-1", "历史商品", "sku-1",
                "历史规格", 2, new BigDecimal("3.00"), new BigDecimal("6.00"));
        SellerOrderView order = new SellerOrderView("order-1", "O0001", "buyer-1", "shop-1",
                "文具店", new BigDecimal("6.00"), Instant.EPOCH, OrderStatus.PAID, List.of(item));
        when(port.getOwnedOrders(any())).thenReturn(CompletableFuture.completedFuture(
                new SellerOrderHistory(List.of(order))));
        SellerOrdersPanel panel = ShopSwingTestSupport.onEdt(() ->
                new SellerOrdersPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        ShopSwingTestSupport.onEdt(() -> ShopSwingTestSupport.component(
                panel, "seller.order.toggle.order-1", JButton.class).doClick());

        assertThat(ShopSwingTestSupport.component(panel,
                "seller.order.item.order-1.sku-1", JLabel.class).getText())
                .contains("历史商品", "历史规格", "2", "6.00");
    }
}
