package edu.seu.vcampus.server.shop.handler;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.shop.SellerApplicationQuery;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.service.ShopAdminService;
import edu.seu.vcampus.server.shop.service.AdminProductService;
import edu.seu.vcampus.common.shop.ProductManagementQuery;
import edu.seu.vcampus.common.shop.AdminProductRef;
import edu.seu.vcampus.common.shop.ProductView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminShopHandlersTest {
    @Test
    void routesAdministrativeSearchWithSessionToken() {
        MessageRouter router = new MessageRouter(Map.of());
        ShopUserPort users = mock(ShopUserPort.class);
        ShopAdminService service = mock(ShopAdminService.class);
        SellerApplicationQuery query = new SellerApplicationQuery(null, null, 0, 20);
        when(users.requireUser("admin-token"))
                .thenReturn(new ShopUser("admin-1", ShopUserKind.ADMINISTRATOR, true));
        when(service.searchApplications("admin-token", query))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        AdminProductService products = mock(AdminProductService.class);
        ProductManagementQuery productQuery = new ProductManagementQuery("shop-1", null, null, 0, 20);
        when(products.searchProducts("admin-token", productQuery))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        AdminProductRef productRef = new AdminProductRef("shop-1", "product-1");
        ProductView product = mock(ProductView.class);
        when(products.getProduct("admin-token", productRef)).thenReturn(product);
        new AdminShopHandlers(router, users, mock(RequestDeduplicator.class), service, products,
                mock(ShopBusinessLogger.class));

        ResponseBody<?> response = router.route(new Message("search", MessageType.REQUEST,
                "SHOP_ADMIN_SEARCH_APPLICATIONS", "admin-token", query, 1L),
                new ClientContext("connection-1", "127.0.0.1"));
        ResponseBody<?> managed = router.route(new Message("products", MessageType.REQUEST,
                "SHOP_ADMIN_SEARCH_PRODUCTS", "admin-token", productQuery, 1L),
                new ClientContext("connection-1", "127.0.0.1"));
        ResponseBody<?> detail = router.route(new Message("product", MessageType.REQUEST,
                "SHOP_ADMIN_GET_PRODUCT", "admin-token", productRef, 1L),
                new ClientContext("connection-1", "127.0.0.1"));

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(managed.code()).isEqualTo("SUCCESS");
        assertThat(detail.data()).isSameAs(product);
        verify(service).searchApplications("admin-token", query);
        verify(products).searchProducts("admin-token", productQuery);
        verify(products).getProduct("admin-token", productRef);
    }
}
