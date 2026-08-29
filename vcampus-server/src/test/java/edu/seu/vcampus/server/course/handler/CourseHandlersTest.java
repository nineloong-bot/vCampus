package edu.seu.vcampus.server.course.handler;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.protocol.*;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.service.*;
import edu.seu.vcampus.server.routing.*;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class CourseHandlersTest {
    private final StubService service = new StubService();
    private final CourseAuthorizationGateway auth = token -> switch (token) {
        case "student" -> new CourseSessionIdentity("u-student", "STUDENT");
        case "teacher" -> new CourseSessionIdentity("u-teacher", "TEACHER");
        case "admin" -> new CourseSessionIdentity("u-admin", "ADMIN");
        default -> null;
    };

    @Test void registersEveryPublishedCommandAndRejectsDuplicateRegistration() {
        MessageRouter router = new MessageRouter(Map.of());
        new CourseHandlers(service, auth, CourseWriteExecutor.direct()).register(router);
        List<String> commands = List.of("COURSE_GET_CURRENT_TERM", "COURSE_SEARCH_OFFERINGS", "COURSE_ENROLL",
                "COURSE_ADJUSTMENT_ADD", "COURSE_ADJUSTMENT_DROP", "COURSE_ADJUSTMENT_CHANGE",
                "COURSE_RETAKE_CHECK", "COURSE_RETAKE_ENROLL", "COURSE_GET_MY_SCHEDULE",
                "COURSE_GET_MY_ENROLLMENTS", "COURSE_IMPORT_OUTCOMES", "COURSE_CREATE",
                "COURSE_UPDATE", "COURSE_CREATE_OFFERING", "COURSE_UPDATE_OFFERING");
        commands.forEach(command -> assertThat(route(router, command, "student", validBody(command)).code())
                .isNotEqualTo("COMMON_INTERNAL_ERROR"));
        ResponseBody<?> duplicate = route(router, "COURSE_ENROLL", "student", new EnrollCommand("o-1"));
        assertThat(duplicate.success()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new CourseHandlers(service, auth, CourseWriteExecutor.direct()).register(router))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void routesManagementAndPhaseCommandsToTheirExactTypedServiceMethods() {
        MessageRouter router = router();
        CreateTermCommand create = (CreateTermCommand) validBody("COURSE_TERM_CREATE");
        UpdateTermCommand update = (UpdateTermCommand) validBody("COURSE_TERM_UPDATE");
        CourseCatalogQuery catalog = (CourseCatalogQuery) validBody("COURSE_CATALOG_SEARCH");
        AdjustmentAuditQuery audit = (AdjustmentAuditQuery) validBody("COURSE_ADJUSTMENT_AUDIT_SEARCH");
        EntityIdRequest phase = (EntityIdRequest) validBody("COURSE_GET_TERM_PHASE");

        ResponseBody<?> termsResponse = route(router, "COURSE_TERM_LIST", "admin", EmptyRequest.INSTANCE);
        assertThat(termsResponse.success()).isTrue();
        assertThat(termsResponse.data()).isInstanceOf(List.class).isEqualTo(service.termListResult);
        assertThat(route(router, "COURSE_TERM_CREATE", "admin", create).data()).isEqualTo(service.createdTermResult);
        assertThat(route(router, "COURSE_TERM_UPDATE", "admin", update).data()).isEqualTo(service.updatedTermResult);
        assertThat(route(router, "COURSE_CATALOG_SEARCH", "admin", catalog).data()).isEqualTo(service.catalogResult);
        assertThat(route(router, "COURSE_ADJUSTMENT_AUDIT_SEARCH", "admin", audit).data()).isEqualTo(service.auditResult);
        assertThat(route(router, "COURSE_GET_TERM_PHASE", "admin", phase).data()).isEqualTo(service.phaseResult);

        assertThat(service.listTermsCalls).isOne();
        assertThat(service.createTermCommands).containsExactly(create);
        assertThat(service.updateTermCommands).containsExactly(update);
        assertThat(service.catalogQueries).containsExactly(catalog);
        assertThat(service.auditQueries).containsExactly(audit);
        assertThat(service.phaseTermIds).containsExactly("t");
    }

    @Test void rejectsStudentsBeforeInvokingAdminManagementServices() {
        MessageRouter router = router();
        for (String command : List.of("COURSE_TERM_CREATE", "COURSE_TERM_UPDATE",
                "COURSE_CATALOG_SEARCH", "COURSE_ADJUSTMENT_AUDIT_SEARCH")) {
            ResponseBody<?> response = route(router, command, "student", validBody(command));
            assertThat(response.success()).as(command).isFalse();
            assertThat(response.code()).as(command).isEqualTo("COMMON_FORBIDDEN");
        }
        assertThat(service.listTermsCalls).isZero();
        assertThat(service.createTermCommands).isEmpty();
        assertThat(service.updateTermCommands).isEmpty();
        assertThat(service.catalogQueries).isEmpty();
        assertThat(service.auditQueries).isEmpty();
    }

    @Test void exposesReadOnlyTermDiscoveryToEveryPublishedRole() {
        MessageRouter router = router();
        for (String token : List.of("student", "teacher", "admin")) {
            ResponseBody<?> response = route(router, "COURSE_TERM_LIST", token, EmptyRequest.INSTANCE);
            assertThat(response.success()).as(token).isTrue();
            assertThat(response.data()).isEqualTo(service.termListResult);
            ResponseBody<?> current = route(router, "COURSE_GET_CURRENT_TERM", token, EmptyRequest.INSTANCE);
            assertThat(current.success()).as(token).isTrue();
            assertThat(current.data()).isEqualTo(service.currentTermResult);
        }
        assertThat(service.listTermsCalls).isEqualTo(3);
        assertThat(service.currentTermCalls).isEqualTo(3);
    }

    @Test void exposesTermPhaseToEveryPublishedRole() {
        MessageRouter router = router();
        for (String token : List.of("student", "teacher", "admin")) {
            ResponseBody<?> response = route(router, "COURSE_GET_TERM_PHASE", token, new EntityIdRequest("term-" + token));
            assertThat(response.success()).as(token).isTrue();
            assertThat(response.data()).as(token).isEqualTo(service.phaseResult);
        }
        assertThat(service.phaseTermIds).containsExactly("term-student", "term-teacher", "term-admin");
    }

    @Test void enforcesRolesAtHandlerBoundaryIncludingAdminOnlyImport() {
        MessageRouter router = router();
        assertThat(route(router, "COURSE_SEARCH_OFFERINGS", "teacher", validBody("COURSE_SEARCH_OFFERINGS")).success()).isTrue();
        assertThat(route(router, "COURSE_ENROLL", "teacher", new EnrollCommand("o-1")).code()).isEqualTo("COMMON_FORBIDDEN");
        assertThat(route(router, "COURSE_GET_MY_SCHEDULE", "teacher", EmptyRequest.INSTANCE).success()).isTrue();
        assertThat(route(router, "COURSE_GET_MY_ENROLLMENTS", "teacher", EmptyRequest.INSTANCE).code()).isEqualTo("COMMON_FORBIDDEN");
        for (String command : List.of("COURSE_ENROLL", "COURSE_ADJUSTMENT_ADD",
                "COURSE_ADJUSTMENT_DROP", "COURSE_ADJUSTMENT_CHANGE", "COURSE_RETAKE_CHECK",
                "COURSE_RETAKE_ENROLL", "COURSE_GET_MY_SCHEDULE", "COURSE_GET_MY_ENROLLMENTS")) {
            assertThat(route(router, command, "admin", validBody(command)).code())
                    .as(command).isEqualTo("COMMON_FORBIDDEN");
        }
        assertThat(route(router, "COURSE_IMPORT_OUTCOMES", "student", validBody("COURSE_IMPORT_OUTCOMES")).code()).isEqualTo("COMMON_FORBIDDEN");
        assertThat(route(router, "COURSE_IMPORT_OUTCOMES", "admin", validBody("COURSE_IMPORT_OUTCOMES")).success()).isTrue();
    }

    @Test void validatesExactBodyAndMapsDomainAndUnknownFailuresSafely() {
        MessageRouter router = router();
        assertThat(route(router, "COURSE_ENROLL", "student", EmptyRequest.INSTANCE).code()).isEqualTo("COMMON_VALIDATION_FAILED");
        assertThat(route(router, "COURSE_GET_MY_SCHEDULE", "student", null).code()).isEqualTo("COMMON_VALIDATION_FAILED");
        service.enrollFailure = new OfferingFullException();
        assertThat(route(router, "COURSE_ENROLL", "student", new EnrollCommand("o-1")).code()).isEqualTo("COURSE_OFFERING_FULL");
        service.enrollFailure = new RuntimeException("SELECT secret FROM /private/db.accdb");
        ResponseBody<?> response = route(router, "COURSE_ENROLL", "student", new EnrollCommand("o-1"));
        assertThat(response.code()).isEqualTo("COMMON_INTERNAL_ERROR");
        assertThat(response.message()).doesNotContain("SELECT", "/private", "RuntimeException");
        assertThat(response.error().traceId()).isNotBlank();
        service.enrollFailure = new edu.seu.vcampus.server.course.domain.CourseConcurrentModificationException();
        ResponseBody<?> conflict = route(router, "COURSE_ENROLL", "student", new EnrollCommand("o-1"));
        assertThat(conflict.code()).isEqualTo("COMMON_CONCURRENT_MODIFICATION");
        assertThat(conflict.message()).contains("刷新");
    }

    @Test void voidCommandsReturnEmptyResponseAndWritesKeepRequestIdentity() {
        List<String> ids = new ArrayList<>();
        CourseWriteExecutor writes = (request, identity, action) -> { ids.add(request.requestId()); return action.get(); };
        MessageRouter router = new MessageRouter(Map.of());
        new CourseHandlers(service, auth, writes).register(router);
        assertThat(route(router, "COURSE_ADJUSTMENT_DROP", "student", new DropCommand("e-1", 0)).data())
                .isEqualTo(EmptyResponse.INSTANCE);
        assertThat(route(router, "COURSE_IMPORT_OUTCOMES", "admin", validBody("COURSE_IMPORT_OUTCOMES")).data())
                .isEqualTo(EmptyResponse.INSTANCE);
        assertThat(ids).contains("r-COURSE_ADJUSTMENT_DROP", "r-COURSE_IMPORT_OUTCOMES");
    }

    private MessageRouter router() { MessageRouter r = new MessageRouter(Map.of()); new CourseHandlers(service, auth, CourseWriteExecutor.direct()).register(r); return r; }
    private static ResponseBody<?> route(MessageRouter r, String c, String t, Serializable b) {
        return r.route(new Message("r-" + c, MessageType.REQUEST, c, t, b, 1), new ClientContext("client", "local"));
    }
    private static Serializable validBody(String c) {
        return switch (c) {
            case "COURSE_SEARCH_OFFERINGS" -> new OfferingSearchQuery(null, null, null, false, 0, 20);
            case "COURSE_TERM_LIST", "COURSE_GET_CURRENT_TERM" -> EmptyRequest.INSTANCE;
            case "COURSE_TERM_CREATE" -> new CreateTermCommand("2026-1","秋",java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2027,1,1),java.time.Instant.EPOCH,java.time.Instant.EPOCH.plusSeconds(1),java.time.Instant.EPOCH.plusSeconds(2),java.time.Instant.EPOCH.plusSeconds(3),"PLANNED");
            case "COURSE_TERM_UPDATE" -> new UpdateTermCommand("t","2026-1","秋",java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2027,1,1),java.time.Instant.EPOCH,java.time.Instant.EPOCH.plusSeconds(1),java.time.Instant.EPOCH.plusSeconds(2),java.time.Instant.EPOCH.plusSeconds(3),"ACTIVE",0);
            case "COURSE_CATALOG_SEARCH" -> new CourseCatalogQuery(null,null,0,20);
            case "COURSE_ADJUSTMENT_AUDIT_SEARCH" -> new AdjustmentAuditQuery(null,null,null,null,0,20);
            case "COURSE_GET_TERM_PHASE" -> new EntityIdRequest("t");
            case "COURSE_ENROLL" -> new EnrollCommand("o-1");
            case "COURSE_ADJUSTMENT_ADD" -> new LateAddCommand("o-1");
            case "COURSE_ADJUSTMENT_DROP" -> new DropCommand("e-1", 0);
            case "COURSE_ADJUSTMENT_CHANGE" -> new ChangeOfferingCommand("e-1", "o-2", 0);
            case "COURSE_RETAKE_CHECK" -> new EntityIdRequest("c-1");
            case "COURSE_RETAKE_ENROLL" -> new RetakeCommand("o-1");
            case "COURSE_GET_MY_SCHEDULE", "COURSE_GET_MY_ENROLLMENTS" -> EmptyRequest.INSTANCE;
            case "COURSE_IMPORT_OUTCOMES" -> new ImportCourseOutcomesCommand(List.of(new ImportCourseOutcomesCommand.OutcomeEntry("s", "c", "t", CourseOutcome.FAILED, "src")));
            case "COURSE_CREATE" -> new CreateCourseCommand("CS1", "Name", java.math.BigDecimal.ONE, 16, null, true);
            case "COURSE_UPDATE" -> new UpdateCourseCommand("c", "CS1", "Name", java.math.BigDecimal.ONE, 16, null, true, 0);
            case "COURSE_CREATE_OFFERING" -> new CreateOfferingCommand("t", "c", "u", "A", 20, "OPEN", List.of());
            case "COURSE_UPDATE_OFFERING" -> new UpdateOfferingCommand("o", "t", "c", "u", "A", 20, "OPEN", 0, List.of());
            default -> throw new IllegalArgumentException(c);
        };
    }

    private static final class StubService implements CourseService {
        private static final Instant TIME = Instant.parse("2026-08-10T00:00:00Z");
        final List<TermView> termListResult = List.of(term("term-list"));
        final TermView createdTermResult = term("term-created");
        final TermView currentTermResult = term("term-current");
        final TermView updatedTermResult = term("term-updated");
        final edu.seu.vcampus.common.paging.PageResult<CourseView> catalogResult =
                new edu.seu.vcampus.common.paging.PageResult<>(List.of(new CourseView("course-catalog", "CS-S", "哨兵课程", BigDecimal.ONE, 16, null, true, 3, TIME, TIME)), 4, 5, 6);
        final edu.seu.vcampus.common.paging.PageResult<AdjustmentAuditView> auditResult =
                new edu.seu.vcampus.common.paging.PageResult<>(List.of(new AdjustmentAuditView("audit-sentinel", "student-sentinel", "ADD", null, "offering-sentinel", "SUCCESS", null, TIME)), 7, 8, 9);
        final TermPhaseView phaseResult = new TermPhaseView("term-phase", "ACTIVE", "ADJUSTMENT", TIME,
                TIME.minusSeconds(40), TIME.minusSeconds(30), TIME.minusSeconds(20), TIME.plusSeconds(20));
        int listTermsCalls;
        int currentTermCalls;
        final List<CreateTermCommand> createTermCommands = new ArrayList<>();
        final List<UpdateTermCommand> updateTermCommands = new ArrayList<>();
        final List<CourseCatalogQuery> catalogQueries = new ArrayList<>();
        final List<AdjustmentAuditQuery> auditQueries = new ArrayList<>();
        final List<String> phaseTermIds = new ArrayList<>();
        RuntimeException enrollFailure;
        public List<TermView> listTerms(){listTermsCalls++;return termListResult;} public TermView getCurrentTerm(){currentTermCalls++;return currentTermResult;} public TermView createTerm(CreateTermCommand c){createTermCommands.add(c);return createdTermResult;} public TermView updateTerm(UpdateTermCommand c){updateTermCommands.add(c);return updatedTermResult;}
        public edu.seu.vcampus.common.paging.PageResult<CourseView> searchCatalog(CourseCatalogQuery q){catalogQueries.add(q);return catalogResult;}
        public edu.seu.vcampus.common.paging.PageResult<AdjustmentAuditView> searchAdjustmentAudits(AdjustmentAuditQuery q){auditQueries.add(q);return auditResult;}
        public TermPhaseView getTermPhase(String id){phaseTermIds.add(id);return phaseResult;}
        public CourseView createCourse(CreateCourseCommand c){return null;} public CourseView updateCourse(UpdateCourseCommand c){return null;}
        public OfferingView createOffering(CreateOfferingCommand c){return null;} public OfferingView updateOffering(UpdateOfferingCommand c){return null;}
        public edu.seu.vcampus.common.paging.PageResult<OfferingSummary> searchOfferings(OfferingSearchQuery q){return new edu.seu.vcampus.common.paging.PageResult<>(List.of(),0,20,0);}
        public EnrollmentView enroll(String t, EnrollCommand c){if(enrollFailure!=null)throw enrollFailure; return new EnrollmentView("e","o","s","NORMAL","ACTIVE",java.time.Instant.EPOCH,null,0);}
        public EnrollmentView addDuringAdjustment(String t,LateAddCommand c){return enroll(t,new EnrollCommand(c.offeringId()));} public void dropDuringAdjustment(String t,DropCommand c){}
        public EnrollmentView changeDuringAdjustment(String t,ChangeOfferingCommand c){return enroll(t,new EnrollCommand(c.targetOfferingId()));}
        public EnrollmentView enrollRetake(String t,RetakeCommand c){return enroll(t,new EnrollCommand(c.offeringId()));}
        public List<ScheduleItem> getCurrentSchedule(String t){return List.of();} public List<EnrollmentView> getCurrentEnrollments(String t){return List.of();}
        public RetakeEligibility checkRetakeEligibility(String t,String c){return new RetakeEligibility(c,false,List.of(),"x");} public void importCourseOutcomes(ImportCourseOutcomesCommand c){}

        private static TermView term(String id) {
            return new TermView(id, "2026-1", id, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 1),
                    TIME.minusSeconds(40), TIME.minusSeconds(30), TIME.minusSeconds(20), TIME.plusSeconds(20),
                    "ACTIVE", 2, TIME, TIME);
        }
    }
}
