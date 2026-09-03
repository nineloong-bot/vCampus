package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.SharedShopUiKitAdapter;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShopStatusPanelTest {
    @Test
    void sharedThemeUsesCompactGovernanceTable() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        ShopStatusPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ShopStatusPanel(port, new SharedShopUiKitAdapter(), () -> { }));

        JTable table = ShopSwingTestSupport.component(panel, "admin.shops.table", JTable.class);
        assertThat(table.getRowHeight()).isEqualTo(34);
        assertThat(table.getShowVerticalLines()).isFalse();
    }

    @Test
    void listsShopsAndResumesSelectedSuspendedVersion() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        ShopAdminSummary suspended = new ShopAdminSummary("shop-1", "seller-1", "文具店",
                "文具", ShopStatus.SUSPENDED, 3, 7);
        when(port.searchShops(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(suspended), 0, 50, 1)));
        when(port.resumeShop(any())).thenReturn(
                CompletableFuture.completedFuture(EmptyResponse.INSTANCE));
        ShopStatusPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ShopStatusPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        JTable table = ShopSwingTestSupport.component(panel, "admin.shops.table", JTable.class);
        assertThat(table.getValueAt(0, 2)).isEqualTo("文具店");
        ShopSwingTestSupport.onEdt(() -> {
            table.setRowSelectionInterval(0, 0);
            ShopSwingTestSupport.component(panel, "admin.shops.resume", JButton.class).doClick();
        });
        ShopSwingTestSupport.flushEdt();

        verify(port).resumeShop(new ResumeShopCommand("shop-1", 7));
    }
}
