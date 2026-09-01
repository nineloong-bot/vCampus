package edu.seu.vcampus.client.student.service;
import edu.seu.vcampus.common.protocol.ResponseBody;
import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
@FunctionalInterface
public interface StudentRequestClient {
    <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
            String command, Serializable body, Duration timeout);
}
