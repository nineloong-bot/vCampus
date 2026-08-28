package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

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
}
