package edu.seu.vcampus.client.course.service;
import edu.seu.vcampus.common.protocol.ResponseBody; import java.io.Serializable; import java.time.Duration; import java.util.concurrent.CompletableFuture;
/** Minimal asynchronous transport used by the course client facade. */
@FunctionalInterface public interface CourseTransport { <T extends Serializable> CompletableFuture<ResponseBody<T>> send(String command,Serializable body,Duration timeout); }
