package edu.seu.vcampus.client.student.service;
import edu.seu.vcampus.common.protocol.ResponseBody;
import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
/**
 * 学籍管理客户端与服务端通信的命令发送客户端接口。
 */
@FunctionalInterface
public interface StudentRequestClient {
    <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
            String command, Serializable body, Duration timeout);
}
