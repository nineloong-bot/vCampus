package edu.seu.vcampus.server.student.handler;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import java.io.Serializable;
import java.util.function.Supplier;
/** Defines the student write executor contract. */
@FunctionalInterface
public interface StudentWriteExecutor {
    /**
     * Performs the execute operation.
     * @param request the request
     * @param principal the principal
     * @param action the action
     * @return the operation result
     */
    ResponseBody<? extends Serializable> execute(Message request, StudentPrincipal principal,
            Supplier<ResponseBody<? extends Serializable>> action);
}
