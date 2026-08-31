package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SellerApplicationPanelTest {
    @Test
    void applicationAndWorkspacesHaveDedicatedRoutes() {
        assertThat(new ShopRoute.SellerApplication()).isInstanceOf(ShopRoute.class);
        assertThat(new ShopRoute.SellerWorkspace()).isInstanceOf(ShopRoute.class);
        assertThat(new ShopRoute.AdminWorkspace()).isInstanceOf(ShopRoute.class);
    }

    @Test
    void rejectedApplicationKeepsIdentityAndShowsReasonForEditing() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        SellerApplicationView rejected = new SellerApplicationView("application-1", "student-1",
                "旧店名", "简介", "文具", "contact", "经营计划",
                SellerApplicationStatus.REJECTED, "请补充说明", "admin-1",
                Instant.EPOCH, Instant.EPOCH, 3);
        when(port.getMyApplication()).thenReturn(CompletableFuture.completedFuture(Optional.of(rejected)));
        SellerApplicationPanel panel = ShopSwingTestSupport.onEdt(() ->
                new SellerApplicationPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();

        assertThat(ShopSwingTestSupport.component(panel, "seller.application.reason", JLabel.class)
                .getText()).contains("请补充说明");
        assertThat(ShopSwingTestSupport.component(panel, "seller.application.name", JTextField.class)
                .isEnabled()).isTrue();
        assertThat(ShopSwingTestSupport.component(panel, "seller.application.save", JButton.class)
                .isEnabled()).isTrue();
    }

    @Test
    void pendingApplicationIsReadOnly() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        SellerApplicationView pending = new SellerApplicationView("application-1", "student-1",
                "店名", "简介", "文具", "contact", "经营计划",
                SellerApplicationStatus.PENDING, null, null, Instant.EPOCH, null, 2);
        when(port.getMyApplication()).thenReturn(CompletableFuture.completedFuture(Optional.of(pending)));
        SellerApplicationPanel panel = ShopSwingTestSupport.onEdt(() ->
                new SellerApplicationPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();

        assertThat(ShopSwingTestSupport.component(panel, "seller.application.name", JTextField.class)
                .isEnabled()).isFalse();
        assertThat(ShopSwingTestSupport.component(panel, "seller.application.submit", JButton.class)
                .isEnabled()).isFalse();
    }
}
