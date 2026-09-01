package edu.seu.vcampus.server.student.handler;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;
/** Applies foundation request-id deduplication to non-admission student writes. */
public final class DeduplicatingStudentWriteExecutor implements StudentWriteExecutor {
    private final RequestDeduplicator deduplicator;
    public DeduplicatingStudentWriteExecutor(RequestDeduplicator deduplicator) {
        this.deduplicator = Objects.requireNonNull(deduplicator);
    }
    @Override @SuppressWarnings("unchecked")
    public ResponseBody<? extends Serializable> execute(Message request, StudentPrincipal principal,
            Supplier<ResponseBody<? extends Serializable>> action) {
        return deduplicator.executeOnce(request, principal.userId(), "student-handler",
                () -> (ResponseBody<Serializable>) action.get());
    }
}
