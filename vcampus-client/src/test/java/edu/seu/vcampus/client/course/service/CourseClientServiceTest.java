package edu.seu.vcampus.client.course.service;
import edu.seu.vcampus.common.course.*; import edu.seu.vcampus.common.error.ErrorDetail; import edu.seu.vcampus.common.protocol.*; import org.junit.jupiter.api.Test;
import java.io.Serializable; import java.time.Duration; import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
class CourseClientServiceTest {
 @Test void sendsExactTypedCommandAndReturnsTypedData(){ RecordingTransport t=new RecordingTransport(); EnrollmentView view=new EnrollmentView("e","o","s","NORMAL","ACTIVE",java.time.Instant.EPOCH,null,0);t.next=CompletableFuture.completedFuture(ResponseBody.success(view));CourseClientService c=new CourseClientService(t);assertThat(c.enroll(new EnrollCommand("o")).join()).isEqualTo(view);assertThat(t.command).isEqualTo("COURSE_ENROLL");assertThat(t.body).isInstanceOf(EnrollCommand.class);assertThat(t.timeout).isEqualTo(Duration.ofSeconds(15)); }
 @Test void mapsFailureToStableUserExceptionWithoutLosingRetryMetadata(){RecordingTransport t=new RecordingTransport();t.next=CompletableFuture.completedFuture(ResponseBody.failure("COURSE_OFFERING_FULL","容量已满",new ErrorDetail("COURSE_OFFERING_FULL","容量已满",Map.of(),"trace",false)));CourseClientException e=catchThrowableOfType(()->new CourseClientService(t).enroll(new EnrollCommand("o")).join(),CompletionException.class).getCause() instanceof CourseClientException ce?ce:null;assertThat(e).isNotNull();assertThat(e.code()).isEqualTo("COURSE_OFFERING_FULL");assertThat(e.traceId()).isEqualTo("trace");}
 @Test void callReturnsWithoutJoiningTheTransportFuture() {
  RecordingTransport transport=new RecordingTransport();
  CompletableFuture<ResponseBody<edu.seu.vcampus.common.paging.PageResult<OfferingSummary>>> response=new CompletableFuture<>();
  transport.next=response;
  ManualExecutor executor=new ManualExecutor();
  CourseClientService service=new CourseClientService(transport,executor);

  CompletableFuture<edu.seu.vcampus.common.paging.PageResult<OfferingSummary>> result=
          service.searchOfferings(new OfferingSearchQuery(null,null,null,false,0,20));

  assertThat(result).isNotDone();
  assertThat(transport.command).isNull();
  executor.runNext();
  assertThat(transport.command).isEqualTo("COURSE_SEARCH_OFFERINGS");
  assertThat(result).isNotDone();
  response.complete(ResponseBody.success(new edu.seu.vcampus.common.paging.PageResult<>(List.of(),0,20,0)));
  assertThat(result.join().items()).isEmpty();
 }
 @Test void blockingTransportRunsOffTheCallerAndDoesNotBlockLaterCallerWork() throws Exception {
  CountDownLatch sendEntered=new CountDownLatch(1),releaseSend=new CountDownLatch(1);
  AtomicReference<Thread> sendThread=new AtomicReference<>();
  ExecutorService executor=Executors.newSingleThreadExecutor();
  Thread caller=Thread.currentThread();
  CourseClientService service=new CourseClientService(new BlockingTransport(sendEntered,releaseSend,sendThread),executor);
  try {
   CompletableFuture<List<TermView>> result=service.listTerms();
   assertThat(sendEntered.await(1,TimeUnit.SECONDS)).isTrue();
   assertThat(result).isNotDone();
   assertThat(sendThread.get()).isNotSameAs(caller);
   assertThat(Thread.currentThread()).isSameAs(caller);
   releaseSend.countDown();
   assertThat(result.join()).isEmpty();
  } finally { releaseSend.countDown();executor.shutdownNow(); }
 }
 @Test void exposesAdminQueriesWithExactCommandsAndAcceptsAnySerializableList(){RecordingTransport t=new RecordingTransport();CourseClientService c=new CourseClientService(t);t.next=CompletableFuture.completedFuture(ResponseBody.success(new LinkedList<TermView>()));assertThat(c.listTerms().join()).isEmpty();assertThat(t.command).isEqualTo("COURSE_TERM_LIST");assertThat(t.body).isSameAs(EmptyRequest.INSTANCE);t.next=CompletableFuture.completedFuture(ResponseBody.success(new edu.seu.vcampus.common.paging.PageResult<CourseView>(List.of(),0,20,0)));assertThat(c.searchCatalog(new CourseCatalogQuery(null,null,0,20)).join().items()).isEmpty();assertThat(t.command).isEqualTo("COURSE_CATALOG_SEARCH");t.next=CompletableFuture.completedFuture(ResponseBody.success(new edu.seu.vcampus.common.paging.PageResult<AdjustmentAuditView>(List.of(),0,20,0)));c.searchAdjustmentAudits(new AdjustmentAuditQuery(null,null,null,null,0,20)).join();assertThat(t.command).isEqualTo("COURSE_ADJUSTMENT_AUDIT_SEARCH");}
 @Test void getsAuthoritativeCurrentTermWithExactCommand(){RecordingTransport t=new RecordingTransport();TermView term=new TermView("current","2026-1","当前学期",java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2027,1,1),java.time.Instant.EPOCH,java.time.Instant.EPOCH.plusSeconds(1),java.time.Instant.EPOCH.plusSeconds(2),java.time.Instant.EPOCH.plusSeconds(3),"ACTIVE",0,java.time.Instant.EPOCH,java.time.Instant.EPOCH);t.next=CompletableFuture.completedFuture(ResponseBody.success(term));CourseClientService c=new CourseClientService(t);assertThat(c.getCurrentTerm().join()).isEqualTo(term);assertThat(t.command).isEqualTo("COURSE_GET_CURRENT_TERM");assertThat(t.body).isSameAs(EmptyRequest.INSTANCE);}
 @Test void mapsTransportAndMalformedResponsesAtFacadeBoundary(){RecordingTransport t=new RecordingTransport();CourseClientService c=new CourseClientService(t);t.next=CompletableFuture.failedFuture(new java.io.IOException("socket"));assertClientCode(()->c.getCurrentSchedule().join(),"COMMON_NETWORK_ERROR");t.next=CompletableFuture.completedFuture(ResponseBody.success(null));assertClientCode(()->c.getCurrentSchedule().join(),"COMMON_PROTOCOL_ERROR");t.next=CompletableFuture.completedFuture(ResponseBody.success("wrong"));assertClientCode(()->c.getCurrentSchedule().join(),"COMMON_PROTOCOL_ERROR");t.next=CompletableFuture.completedFuture(new ResponseBody<>(false,null,null,null,null));assertClientCode(()->c.getCurrentSchedule().join(),"COMMON_PROTOCOL_ERROR");}
 @Test void mapsDirectAndWrappedTimeoutSeparatelyFromNetwork(){RecordingTransport t=new RecordingTransport();CourseClientService c=new CourseClientService(t);t.next=CompletableFuture.failedFuture(new TimeoutException("late"));assertClientFailure(()->c.listTerms().join(),"COMMON_TIMEOUT","请求超时，请稍后重试",true);t.next=CompletableFuture.failedFuture(new CompletionException(new ExecutionException(new TimeoutException("late"))));assertClientFailure(()->c.listTerms().join(),"COMMON_TIMEOUT","请求超时，请稍后重试",true);}
 @Test void authenticationFailureThrownSynchronouslyPreservesItsCodeAndNotifiesOnce(){RecordingTransport t=new RecordingTransport();CourseClientException expected=new CourseClientException("AUTH_SESSION_EXPIRED","expired","trace",false);t.thrown=expected;CourseClientService c=new CourseClientService(t);List<CourseClientException> notifications=new ArrayList<>();c.addAuthenticationFailureListener(notifications::add);assertClientCode(()->c.listTerms().join(),"AUTH_SESSION_EXPIRED");assertThat(notifications).containsExactly(expected);}
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"AUTH_SESSION_EXPIRED","AUTH_ACCOUNT_DISABLED","AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED"})
 void wrappedAuthenticationFuturePreservesItsCodeAndNotifiesOnce(String code){RecordingTransport t=new RecordingTransport();CourseClientException expected=new CourseClientException(code,"authentication failed","trace",false);t.next=CompletableFuture.failedFuture(new CompletionException(new ExecutionException(expected)));CourseClientService c=new CourseClientService(t);List<CourseClientException> notifications=new ArrayList<>();c.addAuthenticationFailureListener(notifications::add);assertClientCode(()->c.listTerms().join(),code);assertThat(notifications).containsExactly(expected);}
 @Test void nonAuthenticationAndNetworkFailuresKeepTheExistingNetworkMappingAndNeverNotifyAuthenticationListeners(){RecordingTransport t=new RecordingTransport();CourseClientService c=new CourseClientService(t);List<CourseClientException> notifications=new ArrayList<>();c.addAuthenticationFailureListener(notifications::add);t.next=CompletableFuture.failedFuture(new CourseClientException("COURSE_TERM_CLOSED","closed",null,false));assertClientCode(()->c.listTerms().join(),"COMMON_NETWORK_ERROR");t.next=CompletableFuture.failedFuture(new java.io.IOException("socket"));assertClientCode(()->c.listTerms().join(),"COMMON_NETWORK_ERROR");assertThat(notifications).isEmpty();}
 @Test void dropUsesTheGeneralCourseCommand(){RecordingTransport transport=new RecordingTransport();transport.next=CompletableFuture.completedFuture(ResponseBody.success(EmptyResponse.INSTANCE));CourseClientService client=new CourseClientService(transport);client.drop(new DropCommand("enrollment-1",3)).join();assertThat(transport.command).isEqualTo("COURSE_DROP");assertThat(transport.body).isEqualTo(new DropCommand("enrollment-1",3));assertThat(transport.timeout).isEqualTo(Duration.ofSeconds(15));}
 @Test void exposesManualSelectionPhaseAndGroupedStudentSelectionCommands(){
  RecordingTransport transport=new RecordingTransport();CourseClientService client=new CourseClientService(transport);
  SelectionPhaseView phase=new SelectionPhaseView("phase-1","term-1","ENROLLMENT","2026-2027秋季学期选课","DRAFT",0,java.time.Instant.EPOCH,java.time.Instant.EPOCH);
  transport.next=CompletableFuture.completedFuture(ResponseBody.success(new ArrayList<>(List.of(phase))));
  assertThat(client.listSelectionPhases().join()).containsExactly(phase);assertThat(transport.command).isEqualTo("COURSE_SELECTION_PHASE_LIST");assertThat(transport.body).isSameAs(EmptyRequest.INSTANCE);assertThat(transport.timeout).isEqualTo(Duration.ofSeconds(10));
  CreateSelectionPhaseCommand create=new CreateSelectionPhaseCommand("term-1","ENROLLMENT","2026-2027秋季学期选课");
  transport.next=CompletableFuture.completedFuture(ResponseBody.success(phase));client.createSelectionPhase(create).join();assertThat(transport.command).isEqualTo("COURSE_SELECTION_PHASE_CREATE");assertThat(transport.body).isEqualTo(create);assertThat(transport.timeout).isEqualTo(Duration.ofSeconds(15));
  StudentSelectionContextView context=new StudentSelectionContextView("term-1","秋季学期","ACTIVE","phase-1","ENROLLMENT",phase.displayTitle(),"OPEN",java.time.Instant.EPOCH,true,null);
  transport.next=CompletableFuture.completedFuture(ResponseBody.success(context));assertThat(client.getStudentSelectionContext().join()).isEqualTo(context);assertThat(transport.command).isEqualTo("COURSE_STUDENT_SELECTION_CONTEXT");assertThat(transport.body).isSameAs(EmptyRequest.INSTANCE);
  CourseSelectionQuery query=new CourseSelectionQuery("term-1","",null,0,20);
  transport.next=CompletableFuture.completedFuture(ResponseBody.success(new edu.seu.vcampus.common.paging.PageResult<CourseSelectionView>(List.of(),0,20,0)));client.searchStudentCourses(query).join();assertThat(transport.command).isEqualTo("COURSE_STUDENT_COURSE_SEARCH");assertThat(transport.body).isEqualTo(query);
 }
 private static void assertClientCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,String code){Throwable failure=catchThrowable(action);while(failure instanceof CompletionException&&failure.getCause()!=null)failure=failure.getCause();assertThat(failure).isInstanceOf(CourseClientException.class);assertThat(((CourseClientException)failure).code()).isEqualTo(code);}
 private static void assertClientFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,String code,String message,boolean retryable){Throwable f=catchThrowable(action);while(f instanceof CompletionException&&f.getCause()!=null)f=f.getCause();assertThat(f).isInstanceOf(CourseClientException.class);CourseClientException e=(CourseClientException)f;assertThat(e.code()).isEqualTo(code);assertThat(e.getMessage()).isEqualTo(message);assertThat(e.retryable()).isEqualTo(retryable);}
 private static final class RecordingTransport implements CourseTransport {String command;Serializable body;Duration timeout;RuntimeException thrown;CompletableFuture<? extends ResponseBody<? extends Serializable>> next;@SuppressWarnings("unchecked") public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(String c,Serializable b,Duration d){command=c;body=b;timeout=d;if(thrown!=null)throw thrown;return (CompletableFuture<ResponseBody<T>>)(CompletableFuture<?>)next;}}
 private static final class ManualExecutor implements Executor {
  private Runnable next;
  public void execute(Runnable command){if(next!=null)throw new IllegalStateException("task already queued");next=command;}
  void runNext(){Runnable task=Objects.requireNonNull(next,"no queued task");next=null;task.run();}
 }
 private record BlockingTransport(CountDownLatch entered,CountDownLatch release,AtomicReference<Thread> thread) implements CourseTransport {
  @SuppressWarnings("unchecked") public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(String c,Serializable b,Duration d){thread.set(Thread.currentThread());entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new IllegalStateException("send was not released");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return (CompletableFuture<ResponseBody<T>>)(CompletableFuture<?>)CompletableFuture.completedFuture(ResponseBody.success(new LinkedList<TermView>()));}
 }
}
