package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EdtSafetyTest {
    @Test
    void responseCallbackUpdatesStatusOnEdt() throws Exception {
        ConnectionStatusPanel[] holder = new ConnectionStatusPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new ConnectionStatusPanel());
        var updated = new CountDownLatch(1);
        var updatedOnEdt = new AtomicBoolean();
        holder[0].addPropertyChangeListener("statusText", event -> {
            updatedOnEdt.set(SwingUtilities.isEventDispatchThread());
            updated.countDown();
        });
        var response = new CompletableFuture<ResponseBody<EmptyResponse>>();

        holder[0].observe(response);
        CompletableFuture.runAsync(() -> response.complete(
                ResponseBody.success(EmptyResponse.INSTANCE))).join();

        assertThat(updated.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(updatedOnEdt).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void connectionStateListenerShowsRealChineseStatusOnEdt() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(ConnectionState.CONNECTED).when(connection).state();
        ConnectionStatusPanel[] holder = new ConnectionStatusPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new ConnectionStatusPanel(connection));
        var listener = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(connection).addStateListener(listener.capture());
        assertThat(holder[0].statusText()).isEqualTo("服务器已连接");
        var updated = new CountDownLatch(1);
        var updatedOnEdt = new AtomicBoolean();
        holder[0].addPropertyChangeListener("statusText", event -> {
            updatedOnEdt.set(SwingUtilities.isEventDispatchThread());
            updated.countDown();
        });

        CompletableFuture.runAsync(() -> listener.getValue().accept(ConnectionState.FAILED)).join();

        assertThat(updated.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(updatedOnEdt).isTrue();
        assertThat(holder[0].statusText()).isEqualTo("服务器连接失败");
    }
}
