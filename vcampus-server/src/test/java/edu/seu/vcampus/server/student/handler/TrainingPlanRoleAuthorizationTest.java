package edu.seu.vcampus.server.student.handler;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.student.TrainingPlanQuery;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.student.service.StudentGradeService;
import edu.seu.vcampus.server.student.service.TrainingPlanService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TrainingPlanRoleAuthorizationTest {
    @Test
    void studentAdministratorCanManagePlansButCollegeAdministratorCannot() {
        TrainingPlanService studentAdminPlans = mock(TrainingPlanService.class);
        assertThat(route("STUDENT_ADMIN", studentAdminPlans).success()).isTrue();
        verify(studentAdminPlans).searchPlans(any());

        TrainingPlanService collegeAdminPlans = mock(TrainingPlanService.class);
        assertThat(route("COLLEGE_ADMIN", collegeAdminPlans).code())
                .isEqualTo("COMMON_FORBIDDEN");
        verify(collegeAdminPlans, never()).searchPlans(any());
    }

    private static edu.seu.vcampus.common.protocol.ResponseBody<?> route(
            String role, TrainingPlanService plans) {
        MessageRouter router = new MessageRouter(Map.of());
        new TrainingPlanHandlers(plans, mock(StudentGradeService.class),
                token -> new StudentPrincipal("operator", Set.of(role), Set.of()),
                (request, principal, action) -> action.get()).register(router);
        Message request = new Message("request", MessageType.REQUEST, "TRAINING_PLAN_LIST",
                "token", new TrainingPlanQuery(null, null, 1, 20), System.currentTimeMillis());
        return router.route(request, new ClientContext("test", "127.0.0.1"));
    }
}
