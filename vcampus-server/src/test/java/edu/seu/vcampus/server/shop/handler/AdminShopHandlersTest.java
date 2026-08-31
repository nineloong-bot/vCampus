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
        new AdminShopHandlers(router, users, mock(RequestDeduplicator.class), service,
                mock(ShopBusinessLogger.class));

        ResponseBody<?> response = router.route(new Message("search", MessageType.REQUEST,
                "SHOP_ADMIN_SEARCH_APPLICATIONS", "admin-token", query, 1L),
                new ClientContext("connection-1", "127.0.0.1"));

        assertThat(response.code()).isEqualTo("SUCCESS");
        verify(service).searchApplications("admin-token", query);
    }
}
