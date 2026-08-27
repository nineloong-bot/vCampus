package edu.seu.vcampus.client.course.service;
import edu.seu.vcampus.client.core.network.ClientConnection; import edu.seu.vcampus.common.course.*; import edu.seu.vcampus.common.paging.PageResult; import edu.seu.vcampus.common.protocol.*;
import java.io.Serializable; import java.time.Duration; import java.util.*; import java.util.concurrent.CompletableFuture;
/** Typed, non-blocking client facade for every published course command. */
public final class CourseClientService {
 private static final Duration READ=Duration.ofSeconds(10),WRITE=Duration.ofSeconds(15); private final CourseTransport transport;
 public CourseClientService(ClientConnection connection){this(connection::send);} public CourseClientService(CourseTransport transport){this.transport=Objects.requireNonNull(transport);}
 public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery q){return call("COURSE_SEARCH_OFFERINGS",q,READ);}
 public CompletableFuture<EnrollmentView> enroll(EnrollCommand c){return call("COURSE_ENROLL",c,WRITE);} public CompletableFuture<EnrollmentView> addDuringAdjustment(LateAddCommand c){return call("COURSE_ADJUSTMENT_ADD",c,WRITE);}
 public CompletableFuture<EmptyResponse> dropDuringAdjustment(DropCommand c){return call("COURSE_ADJUSTMENT_DROP",c,WRITE);} public CompletableFuture<EnrollmentView> changeDuringAdjustment(ChangeOfferingCommand c){return call("COURSE_ADJUSTMENT_CHANGE",c,WRITE);}
 public CompletableFuture<RetakeEligibility> checkRetakeEligibility(String id){return call("COURSE_RETAKE_CHECK",new EntityIdRequest(id),READ);} public CompletableFuture<EnrollmentView> enrollRetake(RetakeCommand c){return call("COURSE_RETAKE_ENROLL",c,WRITE);}
 public CompletableFuture<List<ScheduleItem>> getCurrentSchedule(){return this.<ArrayList<ScheduleItem>>call("COURSE_GET_MY_SCHEDULE",EmptyRequest.INSTANCE,READ).thenApply(List::copyOf);}
 public CompletableFuture<List<EnrollmentView>> getCurrentEnrollments(){return this.<ArrayList<EnrollmentView>>call("COURSE_GET_MY_ENROLLMENTS",EmptyRequest.INSTANCE,READ).thenApply(List::copyOf);}
 public CompletableFuture<EmptyResponse> importOutcomes(ImportCourseOutcomesCommand c){return call("COURSE_IMPORT_OUTCOMES",c,WRITE);} public CompletableFuture<CourseView> createCourse(CreateCourseCommand c){return call("COURSE_CREATE",c,WRITE);} public CompletableFuture<CourseView> updateCourse(UpdateCourseCommand c){return call("COURSE_UPDATE",c,WRITE);} public CompletableFuture<OfferingView> createOffering(CreateOfferingCommand c){return call("COURSE_CREATE_OFFERING",c,WRITE);} public CompletableFuture<OfferingView> updateOffering(UpdateOfferingCommand c){return call("COURSE_UPDATE_OFFERING",c,WRITE);}
 private <T extends Serializable> CompletableFuture<T> call(String command,Serializable body,Duration timeout){return transport.<T>send(command,body,timeout).thenApply(response->{if(response.success())return response.data();var error=response.error();throw new CourseClientException(response.code(),response.message(),error==null?null:error.traceId(),error!=null&&error.retryable());});}
}
