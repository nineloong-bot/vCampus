package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.composition.CourseComposition;
import edu.seu.vcampus.server.course.composition.CourseRuntimeAdapters;
import edu.seu.vcampus.server.course.composition.TemporaryUserStudentGateway;
import edu.seu.vcampus.server.course.service.CourseAuthorizationGateway;
import edu.seu.vcampus.server.course.service.CourseStudentGateway;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.handler.UserHandlers;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.service.PasswordHasher;
import edu.seu.vcampus.server.user.service.UserServiceImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Production composition root for the user and course server modules. */
public final class ApplicationRuntime {
    private final MessageRouter router;
    private final CourseComposition course;
    private final ResourceLockManager resourceLocks;

    private ApplicationRuntime(MessageRouter router, CourseComposition course,
                               ResourceLockManager resourceLocks) {
        this.router = router;
        this.course = course;
        this.resourceLocks = resourceLocks;
    }

    /**
     * Initializes an application's database resource root (with {@code schema/} and {@code seed/})
     * then composes all socket commands around one provider, router, session registry, and lock manager.
     */
    public static ApplicationRuntime create(ConnectionProvider connections, Path databaseResourceRoot,
                                            Clock clock) throws IOException, SQLException {
        return create(connections, databaseResourceRoot, clock, Duration.ofMinutes(30));
    }

    /** Creates a runtime using the configured idle-session timeout. */
    public static ApplicationRuntime create(ConnectionProvider connections, Path databaseResourceRoot,
                                            Clock clock, Duration sessionIdleTimeout)
            throws IOException, SQLException {
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(databaseResourceRoot, "databaseResourceRoot");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(sessionIdleTimeout, "sessionIdleTimeout");
        new ApplicationSchemaInitializer(databaseResourceRoot).initialize(connections);

        ResourceLockManager locks = new StripedResourceLockManager();
        SessionRegistry sessions = new SessionRegistry(clock, sessionIdleTimeout);
        UserServiceImpl users = new UserServiceImpl(new TransactionManager(connections), locks,
                new AccessUserRepository(), new AccessAuditRepository(), new PasswordHasher(), sessions, clock);
        AuthorizationService authorization = new AuthorizationService(sessions);
        CourseAuthorizationGateway courseAuthorization = CourseRuntimeAdapters.authorization(
                authorization::requireSession,
                UserIdentity::userId,
                identity -> identity.role().name(),
                identity -> !identity.restricted(),
                (userId, role) -> users.findActiveUser(userId)
                        .map(identity -> identity.role().name().equals(role)).orElse(false));
        CourseStudentGateway students = TemporaryUserStudentGateway.create(users);
        CourseComposition courses = CourseComposition.create(connections, courseAuthorization,
                students, clock, locks);
        MessageRouter router = new MessageRouter(Map.of(
                "PING", (request, context) -> ResponseBody.success(EmptyResponse.INSTANCE)));
        new UserHandlers(router, users, authorization);
        courses.register(router);
        return new ApplicationRuntime(router, courses, locks);
    }

    /** Returns the application-wide message router. */
    public MessageRouter router() {
        return router;
    }

    /** Returns the composed course runtime. */
    public CourseComposition course() {
        return course;
    }

    /** Returns the application-wide business resource lock manager. */
    public ResourceLockManager resourceLocks() {
        return resourceLocks;
    }
}
