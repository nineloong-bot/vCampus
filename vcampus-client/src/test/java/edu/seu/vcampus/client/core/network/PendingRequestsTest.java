package edu.seu.vcampus.client.core.network;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingRequestsTest {
    private final java.util.concurrent.ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();
    private final PendingRequests pending = new PendingRequests(scheduler);

    @AfterEach
    void stopScheduler() {
        scheduler.shutdownNow();
    }

    @Test
    void completesOnlyMatchingRequest() {
        var first = pending.register("r1", Duration.ofSeconds(1));
        var second = pending.register("r2", Duration.ofSeconds(1));
        Message response = response("r2");

        pending.complete(response);

        assertThat(second).isCompletedWithValue(response);
        assertThat(first).isNotDone();
    }

    @Test
    void timesOutAndRemovesUnansweredRequest() throws Exception {
        var future = pending.register("r1", Duration.ofMillis(20));

        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(pending.size()).isZero();
    }

    private static Message response(String requestId) {
        return new Message(requestId, MessageType.RESPONSE, "PING", null,
                EmptyResponse.INSTANCE, System.currentTimeMillis());
    }
}
