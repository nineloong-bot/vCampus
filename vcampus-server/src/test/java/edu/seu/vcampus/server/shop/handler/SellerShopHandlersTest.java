package edu.seu.vcampus.server.shop.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.service.SellerApplicationService;
import edu.seu.vcampus.server.shop.service.SellerService;
import edu.seu.vcampus.server.shop.service.ProductService;
import edu.seu.vcampus.server.shop.service.SellerOrderService;
import edu.seu.vcampus.common.shop.ProductManagementQuery;
import edu.seu.vcampus.common.paging.PageResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SellerShopHandlersTest {
    @Test
    void routesSessionScopedReadsAndDeduplicatedWrites() {
        MessageRouter router = new MessageRouter(Map.of());
        ShopUserPort users = mock(ShopUserPort.class);
        RequestDeduplicator deduplicator = mock(RequestDeduplicator.class);
        SellerApplicationService service = mock(SellerApplicationService.class);
        when(users.requireUser("seller-token"))
                .thenReturn(new ShopUser("seller-1", ShopUserKind.STUDENT, true));
        when(service.findMyApplication("seller-token")).thenReturn(Optional.empty());
        when(deduplicator.executeOnce(any(), eq("seller-1"), eq("connection-1"), any()))
                .thenAnswer(invocation -> invocation.<Supplier<ResponseBody<?>>>getArgument(3).get());
        ProductService products = mock(ProductService.class);
        ProductManagementQuery query = new ProductManagementQuery("forged-shop", null, null, 0, 20);
        when(products.searchOwnedProducts("seller-token", query))
                .thenReturn(new PageResult<>(java.util.List.of(), 0, 20, 0));
        new SellerShopHandlers(router, users, deduplicator, service, mock(SellerService.class),
                products, mock(SellerOrderService.class), mock(ShopBusinessLogger.class));

        ResponseBody<?> get = router.route(request("get", "SHOP_SELLER_GET_APPLICATION",
                EmptyRequest.INSTANCE), context());
        SaveSellerDraftCommand draft = new SaveSellerDraftCommand(null, "文具店", "简介",
                "文具", "contact", "经营计划", 0);
        ResponseBody<?> save = router.route(request("save", "SHOP_SELLER_SAVE_APPLICATION", draft), context());
        ResponseBody<?> managed = router.route(request("managed", "SHOP_SELLER_SEARCH_PRODUCTS",
                query), context());

        assertThat(get.code()).isEqualTo("SUCCESS");
        assertThat(get.data()).isNull();
        assertThat(save.code()).isEqualTo("SUCCESS");
        assertThat(managed.code()).isEqualTo("SUCCESS");
        verify(service).findMyApplication("seller-token");
        verify(service).saveDraft("seller-token", draft);
        verify(products).searchOwnedProducts("seller-token", query);
        verify(deduplicator).executeOnce(any(), eq("seller-1"), eq("connection-1"), any());
    }

    @Test
    void rejectsForgedApplicantBodyBeforeServiceInvocation() {
        MessageRouter router = new MessageRouter(Map.of());
        ShopUserPort users = mock(ShopUserPort.class);
        when(users.requireUser("seller-token"))
                .thenReturn(new ShopUser("seller-1", ShopUserKind.STUDENT, true));
        SellerApplicationService service = mock(SellerApplicationService.class);
        new SellerShopHandlers(router, users, mock(RequestDeduplicator.class), service,
                mock(ShopBusinessLogger.class));

        assertThat(router.route(request("forged", "SHOP_SELLER_GET_APPLICATION", "other-user"),
                context()).code()).isEqualTo("COMMON_VALIDATION_FAILED");
        verifyNoInteractions(service);
    }

    private static Message request(String id, String command, java.io.Serializable body) {
        return new Message(id, MessageType.REQUEST, command, "seller-token", body, 1L);
    }

    private static ClientContext context() {
        return new ClientContext("connection-1", "127.0.0.1");
    }
}
