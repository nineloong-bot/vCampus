package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminShopEntryTest {
    @Test void administratorsOnlyCreateManagementPagesAndNeverQueryBuyerData() throws Exception {
        for (UserRole role : new UserRole[]{UserRole.SHOP_ADMIN, UserRole.SUPER_ADMIN, UserRole.ADMIN}) {
            ShopClientPort client = mock(ShopClientPort.class, withSettings()
                    .extraInterfaces(AdminShopClientPort.class)
                    .defaultAnswer(call -> new CompletableFuture<>()));
            UserView user = mock(UserView.class);
            when(user.role()).thenReturn(role);
            SwingUtilities.invokeAndWait(() -> {
                ShopModulePanel module = new ShopModulePanel();
                var coordinator = ShopUiInstaller.createCoordinator(module, user, client,
                        new DefaultShopUiKit(), () -> { });
                coordinator.enter();
                coordinator.goHome();
                assertThat(module.getComponentCount()).isEqualTo(1);
                var cards = (javax.swing.JPanel) module.getComponent(0);
                assertThat(cards.getComponentCount()).isEqualTo(1);
                assertThat(cards.getComponent(0)).isInstanceOf(
                        edu.seu.vcampus.client.shop.ui.admin.ShopAdminPanel.class);
                coordinator.dispose();
            });
            assertThat(mockingDetails(client).getInvocations()).allSatisfy(call ->
                    assertThat(call.getMethod().getName()).isIn(
                            "searchApplications", "searchShops", "searchProducts"));
        }
    }
}
