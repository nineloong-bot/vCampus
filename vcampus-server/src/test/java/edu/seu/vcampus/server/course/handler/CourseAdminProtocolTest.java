package edu.seu.vcampus.server.course.handler;
import edu.seu.vcampus.common.course.*; import edu.seu.vcampus.common.protocol.*; import edu.seu.vcampus.server.course.service.*; import edu.seu.vcampus.server.routing.*; import org.junit.jupiter.api.Test;
import java.io.Serializable; import java.math.BigDecimal; import java.time.*; import java.util.*; import static org.assertj.core.api.Assertions.*;
class CourseAdminProtocolTest {
 @Test void exposesTermCatalogAuditAndPhaseContractsWithAdminBoundaries(){CourseService service=org.mockito.Mockito.mock(CourseService.class);MessageRouter r=new MessageRouter(Map.of());new CourseHandlers(service,t->new CourseSessionIdentity(t,"admin".equals(t)?"ADMIN":"STUDENT"),CourseWriteExecutor.direct()).register(r);
  Map<String,Serializable> bodies=Map.of("COURSE_TERM_LIST",EmptyRequest.INSTANCE,"COURSE_TERM_CREATE",new CreateTermCommand("2026-1","秋",LocalDate.now(),LocalDate.now().plusMonths(4),Instant.EPOCH,Instant.EPOCH.plusSeconds(1),Instant.EPOCH.plusSeconds(2),Instant.EPOCH.plusSeconds(3),"PLANNED"),"COURSE_TERM_UPDATE",new UpdateTermCommand("t","2026-1","秋",LocalDate.now(),LocalDate.now().plusMonths(4),Instant.EPOCH,Instant.EPOCH.plusSeconds(1),Instant.EPOCH.plusSeconds(2),Instant.EPOCH.plusSeconds(3),"ACTIVE",0),"COURSE_CATALOG_SEARCH",new CourseCatalogQuery(null,null,0,20),"COURSE_ADJUSTMENT_AUDIT_SEARCH",new AdjustmentAuditQuery(null,null,null,null,0,20),"COURSE_GET_TERM_PHASE",new EntityIdRequest("t"));
  bodies.forEach((command,body)->assertThat(route(r,command,"admin",body).code()).isNotEqualTo("COMMON_INTERNAL_ERROR"));assertThat(route(r,"COURSE_ADJUSTMENT_AUDIT_SEARCH","student",bodies.get("COURSE_ADJUSTMENT_AUDIT_SEARCH")).code()).isEqualTo("COMMON_FORBIDDEN");
 }
 private static ResponseBody<?> route(MessageRouter r,String c,String token,Serializable body){return r.route(new Message(UUID.randomUUID().toString(),MessageType.REQUEST,c,token,body,1),new ClientContext("c","local"));}
}
