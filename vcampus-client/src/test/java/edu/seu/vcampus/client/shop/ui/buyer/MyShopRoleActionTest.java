package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRouteHost;
import edu.seu.vcampus.client.shop.ui.style.SharedShopUiKitAdapter;
import edu.seu.vcampus.common.shop.PaidOrderHistory;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyShopRoleActionTest {
    @Test
    void shopPersonalPageDoesNotDuplicateUserManagerAccountFields() throws Exception {
        SellerShopClientPort seller = mock(SellerShopClientPort.class);
        when(seller.getMyApplication()).thenReturn(
                CompletableFuture.completedFuture(Optional.empty()));
        Fixture fixture = fixture(UserRole.STUDENT, seller);

        assertThat(findNamed(fixture.panel(), "my.user-id")).isNull();
        assertThat(findNamed(fixture.panel(), "my.login-id")).isNull();
        assertThat(findNamed(fixture.panel(), "my.account-status")).isNull();
        assertThat(ShopSwingTestSupport.component(
                fixture.panel(), "my.business.action", JButton.class)).isNotNull();
    }

    @Test
    void studentWithoutApplicationCanOpenApplicationPage() throws Exception {
        SellerShopClientPort seller = mock(SellerShopClientPort.class);
        when(seller.getMyApplication()).thenReturn(
                CompletableFuture.completedFuture(Optional.empty()));
        Fixture fixture = fixture(UserRole.STUDENT, seller);

        ShopSwingTestSupport.onEdt(() -> fixture.panel.load());
        ShopSwingTestSupport.flushEdt();
        JButton action = ShopSwingTestSupport.component(
                fixture.panel, "my.business.action", JButton.class);

        assertThat(action.getText()).isEqualTo("申请开店");
        ShopSwingTestSupport.onEdt(() -> action.doClick());
        assertThat(fixture.routes).containsExactly(new ShopRoute.SellerApplication());
    }

    @Test
    void approvedSellerCanOpenSellerWorkspaceAndAdministratorCanOpenAdministration() throws Exception {
        SellerShopClientPort seller = mock(SellerShopClientPort.class);
        when(seller.getMyApplication()).thenReturn(CompletableFuture.completedFuture(Optional.of(
                new SellerApplicationView("application-1", "user-1", "店铺", "简介", "文具",
                        "contact", "经营计划", SellerApplicationStatus.APPROVED, null, "admin-1",
                        Instant.EPOCH, Instant.EPOCH, 2))));
        Fixture approved = fixture(UserRole.STUDENT, seller);
        ShopSwingTestSupport.onEdt(() -> approved.panel.load());
        ShopSwingTestSupport.flushEdt();
        JButton sellerAction = ShopSwingTestSupport.component(
                approved.panel, "my.business.action", JButton.class);
        assertThat(sellerAction.getText()).isEqualTo("进入卖家工作区");
        ShopSwingTestSupport.onEdt(() -> sellerAction.doClick());
        assertThat(approved.routes).containsExactly(new ShopRoute.SellerWorkspace());

        Fixture admin = fixture(UserRole.ADMIN, null);
        JButton adminAction = ShopSwingTestSupport.component(
                admin.panel, "my.business.action", JButton.class);
        assertThat(adminAction.getText()).isEqualTo("商城管理");
        ShopSwingTestSupport.onEdt(() -> adminAction.doClick());
        assertThat(admin.routes).containsExactly(new ShopRoute.AdminWorkspace());
    }

    private static Fixture fixture(UserRole role, SellerShopClientPort seller) throws Exception {
        ShopClientPort buyer = mock(ShopClientPort.class);
        when(buyer.getPaidOrders()).thenReturn(
                CompletableFuture.completedFuture(new PaidOrderHistory(List.of())));
        List<ShopRoute> routes = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(new ShopRouteHost() {
            @Override public void render(ShopRoute route) { routes.add(route); }
        });
        UserView user = new UserView("user-1", "LOGIN", role, AccountStatus.ACTIVE, false,
                null, 1, LocalDateTime.MIN, LocalDateTime.MIN);
        MyShopPanel panel = ShopSwingTestSupport.onEdt(() -> new MyShopPanel(
                user, buyer, seller, navigator, new SharedShopUiKitAdapter(), () -> { }));
        return new Fixture(panel, routes);
    }

    private record Fixture(MyShopPanel panel, List<ShopRoute> routes) { }

    private static Component findNamed(Container root, String name) {
        if (name.equals(root.getName())) return root;
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
