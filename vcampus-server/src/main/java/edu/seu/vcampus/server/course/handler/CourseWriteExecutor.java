package edu.seu.vcampus.server.course.handler;
import edu.seu.vcampus.common.protocol.*; import edu.seu.vcampus.server.course.service.CourseSessionIdentity; import java.io.Serializable; import java.util.function.Supplier;
/** Course handler seam for application request-id deduplication. */
@FunctionalInterface public interface CourseWriteExecutor {
 /**
  * Performs the execute operation.
  * @param request the request
  * @param identity the identity
  * @param action the action
  * @return the operation result
  */
 ResponseBody<? extends Serializable> execute(Message request, CourseSessionIdentity identity, Supplier<ResponseBody<? extends Serializable>> action);
 /** Direct executor for tests and compositions whose outer router already deduplicates. */
 static CourseWriteExecutor direct(){return (request,identity,action)->action.get();}
}
