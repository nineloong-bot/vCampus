package edu.seu.vcampus.server.course.composition;
import edu.seu.vcampus.server.concurrency.*; import edu.seu.vcampus.server.course.domain.*; import edu.seu.vcampus.server.course.handler.*; import edu.seu.vcampus.server.course.repository.*; import edu.seu.vcampus.server.course.service.*; import edu.seu.vcampus.server.persistence.*; import edu.seu.vcampus.server.routing.*;
import java.io.Serializable; import java.time.Clock; import java.util.Objects;
/** Production course composition seam; one instance owns exactly one shared lock manager. */
public final class CourseComposition {
 private final ResourceLockManager locks; private final CourseService service; private final CourseHandlers handlers;
 private CourseComposition(ConnectionProvider database,CourseAuthorizationGateway authorization,CourseStudentGateway students,Clock clock,ResourceLockManager locks){this.locks=locks;TransactionManager tx=new TransactionManager(database);CourseRepository repository=new AccessCourseRepository();this.service=new CourseServiceImpl(authorization,students,repository,locks,tx,new TermWindowPolicy(),new ScheduleConflictPolicy(),clock);RequestDeduplicator dedup=new RequestDeduplicator(tx,locks);CourseWriteExecutor writes=(request,identity,action)->dedup.executeOnce(request,identity.userId(),"course:"+identity.userId(),()->cast(action.get()));this.handlers=new CourseHandlers(service,authorization,writes);}
 /** Composes course infrastructure without changing the application bootstrap. */
 public static CourseComposition create(ConnectionProvider database,CourseAuthorizationGateway authorization,CourseStudentGateway students,Clock clock){return new CourseComposition(Objects.requireNonNull(database),Objects.requireNonNull(authorization),Objects.requireNonNull(students),Objects.requireNonNull(clock),new StripedResourceLockManager());}
 /** Installs course commands into an application-owned router. */ public void register(MessageRouter router){handlers.register(router);} public CourseService service(){return service;} public ResourceLockManager resourceLocks(){return locks;}
 @SuppressWarnings("unchecked") private static <T extends Serializable> edu.seu.vcampus.common.protocol.ResponseBody<T> cast(edu.seu.vcampus.common.protocol.ResponseBody<? extends Serializable> r){return (edu.seu.vcampus.common.protocol.ResponseBody<T>)r;}
}
