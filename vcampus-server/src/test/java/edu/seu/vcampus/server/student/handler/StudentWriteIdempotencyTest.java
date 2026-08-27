package edu.seu.vcampus.server.student.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StudentWriteIdempotencyTest {
    @Test
    void duplicateWriteRequestExecutesBusinessActionOnce() throws Exception {
        var database = new StudentAccessTestDatabase();
        var executor = new DeduplicatingStudentWriteExecutor(
                new RequestDeduplicator(database.transactions()));
        var calls = new AtomicInteger();
        var request = new Message("8e7c1a21-9d44-4c82-978b-df34326a0341", MessageType.REQUEST,
                "STUDENT_CHANGE_STATUS", "token", EmptyRequest.INSTANCE, System.currentTimeMillis());
        var principal = new StudentPrincipal("admin-1", Set.of("ADMIN"), Set.of());

        var first = executor.execute(request, principal, () -> {
            calls.incrementAndGet();
            return ResponseBody.success(EmptyRequest.INSTANCE);
        });
        var replay = executor.execute(request, principal, () -> {
            calls.incrementAndGet();
            return ResponseBody.success(EmptyRequest.INSTANCE);
        });

        assertThat(first).isEqualTo(replay);
        assertThat(calls).hasValue(1);
    }
}
