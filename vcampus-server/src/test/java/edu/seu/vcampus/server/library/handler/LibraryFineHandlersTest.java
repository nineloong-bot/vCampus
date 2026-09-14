package edu.seu.vcampus.server.library.handler;

import edu.seu.vcampus.server.wallet.service.WalletException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import edu.seu.vcampus.common.library.LibraryFineQuery;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.wallet.WalletOperationResult;
import edu.seu.vcampus.server.library.service.LibraryFineService;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import java.util.Map;
import static org.mockito.Mockito.*;

class LibraryFineHandlersTest {
    @Test void adminQueriesRequirePermissionAndPaymentsUseSessionOwner() {
        var service = mock(LibraryFineService.class);
        var access = mock(LibraryAccessPort.class);
        var router = new MessageRouter(Map.of());
        LibraryFineHandlers.register(router, service, access);
        doThrow(new IllegalStateException("AUTH_FORBIDDEN")).when(access).requirePermission("student", "LIBRARY_ADMIN");
        var forbidden = router.route(new Message("q", MessageType.REQUEST, "LIBRARY_GET_ALL_FINES", "student",
                new LibraryFineQuery(1, 20), 0), new ClientContext("c", "local"));
        assertThat(forbidden.code()).isEqualTo("AUTH_FORBIDDEN");
        verifyNoInteractions(service);
        when(service.pay("student", "loan")).thenReturn(new WalletOperationResult("receipt", 100));
        var request = new Message("p", MessageType.REQUEST, "LIBRARY_PAY_FINE", "student", "loan", 0);
        assertThat(router.route(request, new ClientContext("c", "local")).success()).isTrue();
        assertThat(router.route(request, new ClientContext("c", "local")).success()).isTrue();
        verify(access, times(2)).requireSession("student");
        verify(service, times(2)).pay("student", "loan");
    }

    @Test void insufficientWalletBalanceHasAnActionableSafeMessage() {
        var response = LibraryHandlerErrorMapper.failure(new WalletException("WALLET_INSUFFICIENT_BALANCE"));
        assertThat(response.code()).isEqualTo("WALLET_INSUFFICIENT_BALANCE");
        assertThat(response.message()).contains("余额不足", "充值");
    }
}
