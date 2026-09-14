package edu.seu.vcampus.server.student.handler;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import java.io.Serializable;
import java.util.function.Supplier;
/**
 * 学籍写操作事务执行器接口，提供一致性与防重保证。
 */
@FunctionalInterface
public interface StudentWriteExecutor {
    ResponseBody<? extends Serializable> execute(Message request, StudentPrincipal principal,
            Supplier<ResponseBody<? extends Serializable>> action);
}
