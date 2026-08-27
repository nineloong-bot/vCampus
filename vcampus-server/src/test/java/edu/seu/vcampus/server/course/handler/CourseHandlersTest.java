package edu.seu.vcampus.server.course.handler;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.protocol.*;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.service.*;
import edu.seu.vcampus.server.routing.*;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
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
        List<String> commands = List.of("COURSE_SEARCH_OFFERINGS", "COURSE_ENROLL",
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

    @Test void enforcesRolesAtHandlerBoundaryIncludingAdminOnlyImport() {
        MessageRouter router = router();
        assertThat(route(router, "COURSE_SEARCH_OFFERINGS", "teacher", validBody("COURSE_SEARCH_OFFERINGS")).success()).isTrue();
        assertThat(route(router, "COURSE_ENROLL", "teacher", new EnrollCommand("o-1")).code()).isEqualTo("COMMON_FORBIDDEN");
        assertThat(route(router, "COURSE_GET_MY_SCHEDULE", "teacher", EmptyRequest.INSTANCE).success()).isTrue();
        assertThat(route(router, "COURSE_GET_MY_ENROLLMENTS", "teacher", EmptyRequest.INSTANCE).code()).isEqualTo("COMMON_FORBIDDEN");
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
        RuntimeException enrollFailure;
        public CourseView createCourse(CreateCourseCommand c){return null;} public CourseView updateCourse(UpdateCourseCommand c){return null;}
        public OfferingView createOffering(CreateOfferingCommand c){return null;} public OfferingView updateOffering(UpdateOfferingCommand c){return null;}
        public edu.seu.vcampus.common.paging.PageResult<OfferingSummary> searchOfferings(OfferingSearchQuery q){return new edu.seu.vcampus.common.paging.PageResult<>(List.of(),0,20,0);}
        public EnrollmentView enroll(String t, EnrollCommand c){if(enrollFailure!=null)throw enrollFailure; return new EnrollmentView("e","o","s","NORMAL","ACTIVE",java.time.Instant.EPOCH,null,0);}
        public EnrollmentView addDuringAdjustment(String t,LateAddCommand c){return enroll(t,new EnrollCommand(c.offeringId()));} public void dropDuringAdjustment(String t,DropCommand c){}
        public EnrollmentView changeDuringAdjustment(String t,ChangeOfferingCommand c){return enroll(t,new EnrollCommand(c.targetOfferingId()));}
        public EnrollmentView enrollRetake(String t,RetakeCommand c){return enroll(t,new EnrollCommand(c.offeringId()));}
        public List<ScheduleItem> getCurrentSchedule(String t){return List.of();} public List<EnrollmentView> getCurrentEnrollments(String t){return List.of();}
        public RetakeEligibility checkRetakeEligibility(String t,String c){return new RetakeEligibility(c,false,List.of(),"x");} public void importCourseOutcomes(ImportCourseOutcomesCommand c){}
    }
}
