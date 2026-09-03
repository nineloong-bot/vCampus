package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.SharedShopUiKitAdapter;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SellerWorkspacePanelTest {
    @Test
    void sharedThemeStylesSellerWorkspaceTabs() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        SellerWorkspacePanel panel = ShopSwingTestSupport.onEdt(() ->
                new SellerWorkspacePanel(port, new SharedShopUiKitAdapter(), () -> { }));

        JTabbedPane tabs = ShopSwingTestSupport.component(panel,
                "seller.workspace.tabs", JTabbedPane.class);
        assertThat(panel.getBackground()).isEqualTo(UiColors.BACKGROUND_PAGE);
        assertThat(tabs.getBackground()).isEqualTo(UiColors.BACKGROUND_SUBTLE);
        assertThat(tabs.getFont()).isEqualTo(UiTypography.BODY);
    }

    @Test
    void suspendedShopShowsReasonAndKeepsCategoryAndWritesDisabled() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        when(port.getOwnedShop()).thenReturn(CompletableFuture.completedFuture(new ShopView(
                "shop-1", "owner-1", "文具店", "简介", "文具", "contact",
                ShopStatus.SUSPENDED, "整改中", "admin-1", java.time.Instant.EPOCH, 3)));
        when(port.searchOwnedProducts(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 0, 50, 0)));
        when(port.getOwnedOrders(any())).thenReturn(CompletableFuture.completedFuture(
                new SellerOrderHistory(List.of())));
        SellerWorkspacePanel panel = ShopSwingTestSupport.onEdt(() ->
                new SellerWorkspacePanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();

        assertThat(ShopSwingTestSupport.component(panel, "seller.workspace.tabs", JTabbedPane.class)
                .getTabCount()).isEqualTo(3);
        assertThat(ShopSwingTestSupport.component(panel, "seller.profile.category", JTextField.class)
                .isEditable()).isFalse();
        assertThat(ShopSwingTestSupport.component(panel, "seller.profile.suspension", JLabel.class)
                .getText()).contains("整改中");
        assertThat(ShopSwingTestSupport.component(panel, "seller.profile.save", JButton.class)
                .isEnabled()).isFalse();
    }
}
