package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.user.UserRole;

import java.util.Objects;

/** Creates the single role-filtered course workspace hosted by the shared shell. */
public final class CourseUiComposition {
    private final CourseUiGateway gateway;

    public CourseUiComposition(CourseClientService client) {
        this(new CourseClientGateway(client));
    }

    /** Creates the production course workspace with the shared authenticated user client. */
    public CourseUiComposition(CourseClientService client, UserClientService users) {
        this(new CourseClientGateway(client, users));
    }

    public CourseUiComposition(CourseUiGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public CourseWorkspacePanel workspaceFor(UserRole role) {
        return new CourseWorkspacePanel(gateway, Objects.requireNonNull(role, "role"));
    }
}
