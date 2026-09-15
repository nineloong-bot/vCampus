package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.ResponseBody;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommerceSessionHandoffTest {
    @Test void expiredConcurrentReadsReturnToLoginOnceOnEdt() throws Exception {
        ClientConnection connection=mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.failure("AUTH_SESSION_EXPIRED","expired",null)))
                .when(connection).send(anyString(),any(),any(),anyString());
        AtomicInteger calls=new AtomicInteger();
        var transport=new SocketCommerceTransport(connection,()->{
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();calls.incrementAndGet();
        });
        var first=transport.execute("SHOP2_CATALOG_LIST",EmptyRequest.INSTANCE,"first");
        var second=transport.execute("SHOP2_CART_GET",EmptyRequest.INSTANCE,"second");
        first.handle((v,e)->null).get(5,TimeUnit.SECONDS);
        second.handle((v,e)->null).get(5,TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(()->{});
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void businessFailureDoesNotLogOutTheUser() throws Exception {
        ClientConnection connection=mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.failure("WALLET_INSUFFICIENT_BALANCE","balance",null)))
                .when(connection).send(anyString(),any(),any(),anyString());
        AtomicInteger calls=new AtomicInteger();
        var transport=new SocketCommerceTransport(connection,calls::incrementAndGet);
        transport.execute("WALLET_RECHARGE",EmptyRequest.INSTANCE,"one").handle((v,e)->null).get(5,TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(()->{});
        assertThat(calls.get()).isZero();
    }
}
