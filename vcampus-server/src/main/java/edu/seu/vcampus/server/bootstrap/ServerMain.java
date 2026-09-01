package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.config.ConfigurationException;
import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.student.handler.DeduplicatingStudentWriteExecutor;
import edu.seu.vcampus.server.student.handler.StudentAuthorizationPort;
import edu.seu.vcampus.server.student.handler.StudentHandlers;
import edu.seu.vcampus.server.student.handler.StudentPrincipal;
import edu.seu.vcampus.server.student.numbering.AccessCampusCardNumberGenerator;
import edu.seu.vcampus.server.student.numbering.AccessStudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.StudentProfileApplicationRepository;
import edu.seu.vcampus.server.student.service.StudentAdmissionCoordinator;
import edu.seu.vcampus.server.student.service.StudentOrganizationAdminService;
import edu.seu.vcampus.server.student.service.StudentServiceImpl;
import edu.seu.vcampus.server.student.service.StudentProfileServiceImpl;
import edu.seu.vcampus.server.student.pdf.StudentProfilePdfService;
import edu.seu.vcampus.server.user.handler.UserHandlers;
import edu.seu.vcampus.server.user.handler.SecurityAuditHandler;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessPermissionRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.service.PasswordHasher;
import edu.seu.vcampus.server.user.service.SecurityAuditService;
import edu.seu.vcampus.server.user.service.UserAccountProvisioningPort;
import edu.seu.vcampus.server.user.service.UserAccountProvisioningService;
import edu.seu.vcampus.server.user.service.UserQueryPort;
import edu.seu.vcampus.server.user.service.UserService;
import edu.seu.vcampus.server.user.service.UserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Validates configuration and starts the VCampus socket server. */
public final class ServerMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMain.class);

    /** Seed administrator used as the fallback operator for student change records. */
    private static final String SYSTEM_OPERATOR_USER_ID =
            "00000000-0000-0000-0000-000000000001";

    private ServerMain() {
    }

    /** Starts the server using an optional path to server.properties. */
    public static void main(String[] args) {
        Path configFile = Path.of(args.length == 0 ? "config/server.properties" : args[0])
                .toAbsolutePath().normalize();
        Path configDirectory = configFile.getParent();
        Path baseDirectory = configDirectory == null ? Path.of(".").toAbsolutePath()
                : configDirectory.getParent();
        if (baseDirectory == null) {
            baseDirectory = Path.of(".").toAbsolutePath();
        }
        try {
            run(ServerConfig.load(configFile, baseDirectory));
        } catch (ConfigurationException error) {
            System.err.println("服务端启动失败：" + error.getMessage());
            System.exit(2);
        } catch (Exception error) {
            LOGGER.error("服务端异常退出", error);
            System.err.println("服务端启动失败：请检查端口、数据库和日志配置。");
            System.exit(3);
        }
    }

    private static void run(ServerConfig config) throws Exception {
        MessageRouter router = new MessageRouter(Map.of(
                "PING", (request, context) -> ResponseBody.success(EmptyResponse.INSTANCE)));
        ServerRuntime runtime = createRuntime(config);
        new UserHandlers(router, runtime.users(), runtime.authorization(), runtime.deduplicator());
        registerSecurityAudit(router, runtime.auditHandler());
        runtime.students().register(router);
        SocketServer server = new SocketServer(config.port(), config.workerThreads(),
                config.maxConnections(), router);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(server), "vcampus-shutdown"));
        LOGGER.info("VCampus 服务端已启动，监听端口 {}", config.port());
        server.serve();
    }

    private static ServerRuntime createRuntime(ServerConfig config) {
        String databaseUrl = "jdbc:ucanaccess://" + config.databasePath()
                + ";immediatelyReleaseResources=true";
        ConnectionProvider connections = () -> DriverManager.getConnection(databaseUrl);
        java.time.Clock clock = java.time.Clock.systemUTC();
        TransactionManager transactions = new TransactionManager(connections);
        StripedResourceLockManager locks = new StripedResourceLockManager();
        SessionRegistry sessions = new SessionRegistry(clock,
                Duration.ofMinutes(config.sessionTimeoutMinutes()));
        AccessUserRepository userRepository = new AccessUserRepository();
        AccessAuditRepository audits = new AccessAuditRepository();
        PasswordHasher passwords = new PasswordHasher();
        UserService users = new UserServiceImpl(transactions, locks,
                userRepository, new AccessPermissionRepository(),
                audits, passwords, sessions, clock);
        AuthorizationService authorization = new AuthorizationService(sessions);
        RequestDeduplicator deduplicator = new RequestDeduplicator(transactions, locks);
        SecurityAuditHandler auditHandler = new SecurityAuditHandler(authorization,
                new SecurityAuditService(transactions, audits));
        StudentHandlers students = createStudentHandlers(transactions, locks, sessions,
                deduplicator, (UserQueryPort) users, userRepository, audits, passwords);
        return new ServerRuntime(users, authorization, deduplicator, auditHandler, students);
    }

    private static void registerSecurityAudit(
            MessageRouter router, MessageHandler handler) {
        router.register("SECURITY_AUDIT_SEARCH", handler);
    }

    private static StudentHandlers createStudentHandlers(TransactionManager transactions,
            ResourceLockManager locks, SessionRegistry sessions,
            RequestDeduplicator deduplicator, UserQueryPort users,
            AccessUserRepository userRepository, AccessAuditRepository audits,
            PasswordHasher passwords) {
        StudentRepository students = new StudentRepository();
        StudentChangeRepository changes = new StudentChangeRepository();
        OrganizationRepository organizations = new AccessOrganizationRepository();
        NumberSequenceRepository sequences = new NumberSequenceRepository();
        UserAccountProvisioningPort accounts = new UserAccountProvisioningService(locks,
                userRepository, audits, passwords);
        StudentAdmissionCoordinator admissions = new StudentAdmissionCoordinator(
                transactions, locks, deduplicator, organizations,
                new AccessCampusCardNumberGenerator(sequences),
                new AccessStudentNumberGenerator(sequences), accounts, students, changes);
        StudentServiceImpl service = new StudentServiceImpl(transactions, locks, students,
                changes, organizations, users, SYSTEM_OPERATOR_USER_ID);
        StudentProfileServiceImpl profiles = new StudentProfileServiceImpl(transactions, locks,
                students, new StudentProfileApplicationRepository(), changes, users);
        StudentAuthorizationPort authorization = token -> {
            SessionRegistry.SessionSnapshot snapshot;
            try {
                snapshot = sessions.requireSnapshot(token);
            } catch (SessionExpiredException error) {
                throw new IllegalArgumentException("Invalid session", error);
            }
            if (snapshot.restricted()) {
                throw new IllegalArgumentException("Invalid session");
            }
            UserIdentity identity = snapshot.identity();
            return new StudentPrincipal(identity.userId(), Set.of(identity.role().name()),
                    snapshot.permissions());
        };
        return new StudentHandlers(admissions, service,
                new StudentOrganizationAdminService(transactions, locks, organizations),
                authorization, new DeduplicatingStudentWriteExecutor(deduplicator), profiles,
                new StudentProfilePdfService());
    }

    private static void shutdown(SocketServer server) {
        try {
            server.stopAccepting();
            if (!server.awaitRequests(Duration.ofSeconds(30))) {
                LOGGER.warn("等待中的请求超过 30 秒，将中止剩余任务");
            }
            server.close();
        } catch (Exception error) {
            LOGGER.warn("服务端停机清理未完全成功", error);
        }
    }

    private record ServerRuntime(UserService users, AuthorizationService authorization,
                                 RequestDeduplicator deduplicator,
                                 SecurityAuditHandler auditHandler,
                                 StudentHandlers students) {
    }
}
