package edu.seu.vcampus.server.course.handler;

import edu.seu.vcampus.common.course.*; import edu.seu.vcampus.common.error.ErrorDetail; import edu.seu.vcampus.common.protocol.*;
import edu.seu.vcampus.server.course.domain.*; import edu.seu.vcampus.server.course.service.*; import edu.seu.vcampus.server.routing.*;
import edu.seu.vcampus.server.security.InitialPasswordChangeRequiredException; import edu.seu.vcampus.server.security.SessionExpiredException;
import java.io.Serializable; import java.util.*; import java.util.function.BiFunction;

/** Registers the complete published course protocol with boundary authorization and safe errors. */
public final class CourseHandlers {
 private final CourseService service; private final CourseAuthorizationGateway authorization; private final CourseWriteExecutor writes;
 public CourseHandlers(CourseService service,CourseAuthorizationGateway authorization,CourseWriteExecutor writes){this.service=Objects.requireNonNull(service);this.authorization=Objects.requireNonNull(authorization);this.writes=Objects.requireNonNull(writes);}
 /** Claims all exact commands from the course specification. */
 public void register(MessageRouter r){
  r.register("COURSE_TERM_LIST",read(EmptyRequest.class,Set.of("STUDENT","TEACHER","ADMIN"),(m,b)->(Serializable)new ArrayList<>(service.listTerms())));
  r.register("COURSE_GET_CURRENT_TERM",read(EmptyRequest.class,Set.of("STUDENT","TEACHER","ADMIN"),(m,b)->service.getCurrentTerm()));
  r.register("COURSE_TERM_CREATE",write(CreateTermCommand.class,Set.of("ADMIN"),(m,b)->service.createTerm(b)));
  r.register("COURSE_TERM_UPDATE",write(UpdateTermCommand.class,Set.of("ADMIN"),(m,b)->service.updateTerm(b)));
  r.register("COURSE_CATALOG_SEARCH",read(CourseCatalogQuery.class,Set.of("ADMIN"),(m,b)->service.searchCatalog(b)));
  r.register("COURSE_ADJUSTMENT_AUDIT_SEARCH",read(AdjustmentAuditQuery.class,Set.of("ADMIN"),(m,b)->service.searchAdjustmentAudits(b)));
  r.register("COURSE_GET_TERM_PHASE",read(EntityIdRequest.class,Set.of("STUDENT","TEACHER","ADMIN"),(m,b)->service.getTermPhase(b.entityId())));
  r.register("COURSE_SEARCH_OFFERINGS",read(OfferingSearchQuery.class,Set.of("STUDENT","TEACHER","ADMIN"),(m,b)->service.searchOfferings(b)));
  r.register("COURSE_ENROLL",write(EnrollCommand.class,Set.of("STUDENT"),(m,b)->service.enroll(m.sessionToken(),b)));
  r.register("COURSE_ADJUSTMENT_ADD",write(LateAddCommand.class,Set.of("STUDENT"),(m,b)->service.addDuringAdjustment(m.sessionToken(),b)));
  r.register("COURSE_ADJUSTMENT_DROP",write(DropCommand.class,Set.of("STUDENT"),(m,b)->{service.dropDuringAdjustment(m.sessionToken(),b);return EmptyResponse.INSTANCE;}));
  r.register("COURSE_ADJUSTMENT_CHANGE",write(ChangeOfferingCommand.class,Set.of("STUDENT"),(m,b)->service.changeDuringAdjustment(m.sessionToken(),b)));
  r.register("COURSE_RETAKE_CHECK",read(EntityIdRequest.class,Set.of("STUDENT"),(m,b)->service.checkRetakeEligibility(m.sessionToken(),b.entityId())));
  r.register("COURSE_RETAKE_ENROLL",write(RetakeCommand.class,Set.of("STUDENT"),(m,b)->service.enrollRetake(m.sessionToken(),b)));
  r.register("COURSE_GET_MY_SCHEDULE",read(EmptyRequest.class,Set.of("STUDENT","TEACHER"),(m,b)->(Serializable)new ArrayList<>(service.getCurrentSchedule(m.sessionToken()))));
  r.register("COURSE_GET_MY_ENROLLMENTS",read(EmptyRequest.class,Set.of("STUDENT"),(m,b)->(Serializable)new ArrayList<>(service.getCurrentEnrollments(m.sessionToken()))));
  r.register("COURSE_IMPORT_OUTCOMES",write(ImportCourseOutcomesCommand.class,Set.of("ADMIN"),(m,b)->{service.importCourseOutcomes(b);return EmptyResponse.INSTANCE;}));
  r.register("COURSE_CREATE",write(CreateCourseCommand.class,Set.of("ADMIN"),(m,b)->service.createCourse(b)));
  r.register("COURSE_UPDATE",write(UpdateCourseCommand.class,Set.of("ADMIN"),(m,b)->service.updateCourse(b)));
  r.register("COURSE_CREATE_OFFERING",write(CreateOfferingCommand.class,Set.of("ADMIN"),(m,b)->service.createOffering(b)));
  r.register("COURSE_UPDATE_OFFERING",write(UpdateOfferingCommand.class,Set.of("ADMIN"),(m,b)->service.updateOffering(b)));
 }
 private <B extends Serializable> MessageHandler read(Class<B> type,Set<String> roles,BiFunction<Message,B,? extends Serializable> fn){return boundary(type,roles,false,fn);}
 private <B extends Serializable> MessageHandler write(Class<B> type,Set<String> roles,BiFunction<Message,B,? extends Serializable> fn){return boundary(type,roles,true,fn);}
 private <B extends Serializable> MessageHandler boundary(Class<B> type,Set<String> roles,boolean write,BiFunction<Message,B,? extends Serializable> fn){return (message,context)->{
  try{CourseSessionIdentity id=requireRole(message,roles);if(message.body()==null||message.body().getClass()!=type)return validation();B body=type.cast(message.body());var action=(java.util.function.Supplier<ResponseBody<? extends Serializable>>)(()->safe(()->fn.apply(message,body)));return write?writes.execute(message,id,action):action.get();}catch(RuntimeException e){return failure(e);}
 };}
 private CourseSessionIdentity requireRole(Message m,Set<String> roles){if(m.sessionToken()==null||m.sessionToken().isBlank())throw new SessionExpiredException();CourseSessionIdentity id=authorization.requireSession(m.sessionToken());if(id==null||!roles.contains(id.role()))throw new CourseForbiddenException();return id;}
 private static ResponseBody<? extends Serializable> safe(java.util.concurrent.Callable<? extends Serializable> action){try{return ResponseBody.success(action.call());}catch(Exception e){return failure(e);}}
 private static ResponseBody<? extends Serializable> validation(){return ResponseBody.failure("COMMON_VALIDATION_FAILED","请求内容无效",new ErrorDetail("COMMON_VALIDATION_FAILED","请求内容无效",Map.of(),null,false));}
 private static ResponseBody<? extends Serializable> failure(Throwable e){if(e instanceof SessionExpiredException)return authenticationFailure("AUTH_SESSION_EXPIRED","会话已过期，请重新登录");if(e instanceof InitialPasswordChangeRequiredException)return authenticationFailure("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED","请先修改初始密码");if(e instanceof CourseRuleException rule)return ResponseBody.failure(rule.code(),userMessage(rule.code()),new ErrorDetail(rule.code(),userMessage(rule.code()),Map.of(),null,false));if(e instanceof IllegalArgumentException||e instanceof NullPointerException)return validation();String trace=UUID.randomUUID().toString();return ResponseBody.failure("COMMON_INTERNAL_ERROR","服务暂时不可用，请稍后重试",new ErrorDetail("COMMON_INTERNAL_ERROR","服务暂时不可用，请稍后重试",Map.of(),trace,true));}
 private static ResponseBody<? extends Serializable> authenticationFailure(String code,String message){return ResponseBody.failure(code,message,new ErrorDetail(code,message,Map.of(),null,false));}
 private static String userMessage(String code){return switch(code){case "COMMON_CONCURRENT_MODIFICATION"->"数据已被其他操作修改，请刷新后重试";case "COMMON_FORBIDDEN"->"没有执行该操作的权限，请重新登录或联系管理员";case "COURSE_OFFERING_FULL"->"教学班容量已满，请选择其他教学班";case "COURSE_OFFERING_HAS_ENROLLMENTS"->"已有学生选课，不能修改所属学期、课程或上课安排";case "COURSE_SCHEDULE_CONFLICT"->"所选教学班与当前课表冲突，请调整选择";case "COURSE_DUPLICATE_ENROLLMENT"->"本学期已选择该课程，请先查看当前选课";case "COURSE_ENROLLMENT_NOT_OPEN"->"当前不在选课时间内，请查看开放时间";case "COURSE_ADJUSTMENT_NOT_OPEN"->"当前不在退改补时间内，请查看调整时间";case "COURSE_STUDENT_INELIGIBLE"->"当前学籍状态不允许选课，请联系教务人员";case "COURSE_ENROLLMENT_NOT_ACTIVE"->"选课记录已失效，请刷新后重试";case "COURSE_CHANGE_TARGET_INVALID"->"目标教学班不可用于改选，请重新选择";case "COURSE_RETAKE_NOT_ELIGIBLE"->"未找到不通过的修读记录，暂不能重修";case "COURSE_OUTCOME_IMPORT_INVALID"->"导入内容无效或来源冲突，请核对后重试";default->"操作未完成，请刷新数据后重试";};}
}
