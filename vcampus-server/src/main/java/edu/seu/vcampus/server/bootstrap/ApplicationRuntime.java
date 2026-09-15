package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.composition.CourseComposition;
import edu.seu.vcampus.server.course.composition.CourseRuntimeAdapters;
import edu.seu.vcampus.server.course.service.CourseAuthorizationGateway;
import edu.seu.vcampus.server.course.service.CourseStudentGateway;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.handler.SecurityAuditHandler;
import edu.seu.vcampus.server.user.handler.UserHandlers;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessPermissionRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.service.PasswordHasher;
import edu.seu.vcampus.server.user.service.SecurityAuditService;
import edu.seu.vcampus.server.user.service.UserQueryPort;
import edu.seu.vcampus.server.user.service.UserServiceImpl;
import edu.seu.vcampus.server.student.service.StudentQueryPort;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Production composition root for the user and course server modules. */
public final class ApplicationRuntime implements AutoCloseable {
    private edu.seu.vcampus.server.shop.composition.CommerceRuntime commerce;
    private final MessageRouter router;
    private final CourseComposition course;
    private final ResourceLockManager resourceLocks;
    private final AuthorizationService authorization;

    private ApplicationRuntime(MessageRouter router, CourseComposition course,
                               ResourceLockManager resourceLocks,
                               AuthorizationService authorization) {
        this.router = router;
        this.course = course;
        this.resourceLocks = resourceLocks;
        this.authorization = authorization;
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
        return createUnified(connections, databaseResourceRoot, clock, sessionIdleTimeout, null);
    }

    /** Creates a runtime with an application-specific course-to-student adapter. */
    public static ApplicationRuntime create(ConnectionProvider connections, Path databaseResourceRoot,
                                            Clock clock, Duration sessionIdleTimeout,
                                            Function<UserQueryPort, CourseStudentGateway> studentGatewayFactory)
            throws IOException, SQLException {
        Objects.requireNonNull(studentGatewayFactory, "studentGatewayFactory");
        return createUnified(connections, databaseResourceRoot, clock, sessionIdleTimeout,
                studentGatewayFactory);
    }

    private static ApplicationRuntime createUnified(ConnectionProvider connections,
                                            Path databaseResourceRoot, Clock clock,
                                            Duration sessionIdleTimeout,
                                            Function<UserQueryPort, CourseStudentGateway> studentGatewayFactory)
            throws IOException, SQLException {
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(databaseResourceRoot, "databaseResourceRoot");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(sessionIdleTimeout, "sessionIdleTimeout");
        new ApplicationSchemaInitializer(databaseResourceRoot).initialize(connections);
        new edu.seu.vcampus.server.wallet.WalletSchemaInitializer(
                databaseResourceRoot.resolve("schema/051_shop_wallet.sql")).initialize(connections);
        new edu.seu.vcampus.server.shop.composition.CommerceSchemaInitializer(
                databaseResourceRoot.resolve("schema")).initialize(connections);

        ResourceLockManager locks = new StripedResourceLockManager();
        SessionRegistry sessions = new SessionRegistry(clock, sessionIdleTimeout);
        TransactionManager transactions = new TransactionManager(connections);
        AccessAuditRepository audits = new AccessAuditRepository();
        AccessUserRepository userRepository = new AccessUserRepository();
        PasswordHasher passwords = new PasswordHasher();
        RequestDeduplicator deduplicator = new RequestDeduplicator(transactions, locks);
        UserServiceImpl users = new UserServiceImpl(transactions, locks,
                userRepository, new AccessPermissionRepository(),
                audits, passwords, sessions, clock);
        AuthorizationService authorization = new AuthorizationService(sessions);
        CourseAuthorizationGateway courseAuthorization = CourseRuntimeAdapters.authorization(
                sessions::requireSnapshot,
                snapshot -> snapshot.identity().userId(),
                snapshot -> courseRole(snapshot.identity().role()),
                snapshot -> !snapshot.restricted(),
                (userId, role) -> users.findActiveUser(userId)
                        .map(identity -> identity.role().name().equals(role)).orElse(false));
        MessageRouter router = new MessageRouter(Map.of(
                "PING", (request, context) -> ResponseBody.success(EmptyResponse.INSTANCE)));
        new UserHandlers(router, users, authorization, deduplicator);
        new edu.seu.vcampus.server.wallet.handler.WalletHandlers(router,
                new edu.seu.vcampus.server.wallet.service.WalletService(
                        transactions, new StripedResourceLockManager(), clock), sessions);
        StudentGovernanceRegistry.register(router, transactions, locks, sessions,
                authorization, deduplicator, audits, userRepository, passwords);
        router.register("SECURITY_AUDIT_SEARCH", new SecurityAuditHandler(authorization,
                new SecurityAuditService(transactions, audits)));
        StudentQueryPort studentQueries = UnifiedModuleRegistry.registerStudent(router,
                transactions, locks, sessions, deduplicator, users, userRepository, audits,
                passwords);
        CourseStudentGateway students = studentGatewayFactory == null
                ? CourseRuntimeAdapters.students(
                        studentQueries::getEnrollmentEligibility,
                        studentQueries::getEnrollmentEligibilityByStudentNumber,
                        eligibility -> eligibility.studentId(),
                        eligibility -> eligibility.status().name(),
                        eligibility -> eligibility.majorCode(),
                        eligibility -> eligibility.cohortYear(),
                        studentQueries::existsActiveStudent)
                : Objects.requireNonNull(studentGatewayFactory.apply(users), "studentGateway");
        CourseComposition courses = CourseComposition.create(connections, courseAuthorization,
                students, clock, locks);
        courses.register(router);
        UnifiedModuleRegistry.registerLibraryAndShop(router, transactions, locks, sessions,
                authorization, deduplicator, clock);
        ApplicationRuntime runtime = new ApplicationRuntime(router, courses, locks, authorization);
        runtime.commerce = new edu.seu.vcampus.server.shop.composition.CommerceRuntime(
                router, transactions, locks, sessions, clock);
        return runtime;
    }

    /** Starts commerce expiry and recovery tasks after the application has been composed. */
    public void startMaintenance() { if (commerce != null) commerce.start(); }

    /** Stops owned commerce maintenance tasks during shutdown. */
    @Override public void close() { if (commerce != null) commerce.close(); }

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

    /** Exposes the shared authorization state to the production bootstrap. */
    AuthorizationService authorization() {
        return authorization;
    }

    private static String courseRole(UserRole role) {
        return switch (role) {
            case SUPER_ADMIN, COURSE_ADMIN, ADMIN -> "ADMIN";
            default -> role.name();
        };
    }
}
