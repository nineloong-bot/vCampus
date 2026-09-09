package edu.seu.vcampus.server.student.majortransfer.handler;

import edu.seu.vcampus.common.protocol.*;
import edu.seu.vcampus.common.student.EntityIdRequest;
import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.routing.*;
import edu.seu.vcampus.server.student.handler.*;
import edu.seu.vcampus.server.student.majortransfer.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MajorTransferHandlersTest {
    @Test void nonAdminCannotDownloadMaterialsOrReplayAdminWrites() {
        var service = mock(MajorTransferService.class);
        for (String role : List.of("STUDENT", "TEACHER")) {
            var router = new MessageRouter(Map.of());
            new MajorTransferHandlers(service, token -> new StudentPrincipal("user", Set.of(role), Set.of("STUDENT_WRITE")),
                    (request, principal, action) -> { throw new AssertionError("Unauthorized request reached deduplicator"); }).register(router);
            assertThat(router.route(request("MAJOR_TRANSFER_GET_ATTACHMENT", new EntityIdRequest("attachment")), client()).code())
                    .isEqualTo("COMMON_FORBIDDEN");
            assertThat(router.route(request("MAJOR_TRANSFER_EXECUTE", new ExecuteMajorTransferCommand("app", "class", 0)), client()).code())
                    .isEqualTo("COMMON_FORBIDDEN");
        }
        verifyNoInteractions(service);
    }

    @Test void validationFailuresAreReturnedInsideDeduplicationBoundary() {
        var service = mock(MajorTransferService.class);
        when(service.submit(anyString(), any())).thenThrow(new MajorTransferException("TRANSFER_BATCH_CLOSED", "已截止"));
        var router = new MessageRouter(Map.of());
        AtomicReference<ResponseBody<?>> persisted = new AtomicReference<>();
        new MajorTransferHandlers(service, token -> new StudentPrincipal("student", Set.of("STUDENT"), Set.of()),
                (request, principal, action) -> { var result = action.get(); persisted.set(result); return result; }).register(router);
        var result = router.route(request("MAJOR_TRANSFER_SUBMIT", new SubmitMajorTransferCommand("app", 0)), client());
        assertThat(result.code()).isEqualTo("TRANSFER_BATCH_CLOSED");
        assertThat(persisted.get().code()).isEqualTo("TRANSFER_BATCH_CLOSED");
    }

    private static Message request(String command, java.io.Serializable body) {
        return new Message("same-request", MessageType.REQUEST, command, "token", body, System.currentTimeMillis());
    }
    private static ClientContext client() { return new ClientContext("test", "127.0.0.1"); }
}
