package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
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
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.handler.UserHandlers;
import edu.seu.vcampus.server.user.handler.SecurityAuditHandler;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessPermissionRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.service.PasswordHasher;
import edu.seu.vcampus.server.user.service.SecurityAuditService;
import edu.seu.vcampus.server.user.service.UserService;
import edu.seu.vcampus.server.user.service.UserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;

/** Validates configuration and starts the VCampus socket server. */
public final class ServerMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMain.class);

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
        UserRuntime users = createUserRuntime(config);
        new UserHandlers(router, users.service(), users.authorization(), users.deduplicator());
        registerSecurityAudit(router, users.auditHandler());
        SocketServer server = new SocketServer(config.port(), config.workerThreads(),
                config.maxConnections(), router);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(server), "vcampus-shutdown"));
        LOGGER.info("VCampus 服务端已启动，监听端口 {}", config.port());
        server.serve();
    }

    private static UserRuntime createUserRuntime(ServerConfig config) {
        String databaseUrl = "jdbc:ucanaccess://" + config.databasePath()
                + ";immediatelyReleaseResources=true";
        ConnectionProvider connections = () -> DriverManager.getConnection(databaseUrl);
        java.time.Clock clock = java.time.Clock.systemUTC();
        TransactionManager transactions = new TransactionManager(connections);
        StripedResourceLockManager locks = new StripedResourceLockManager();
        SessionRegistry sessions = new SessionRegistry(clock,
                Duration.ofMinutes(config.sessionTimeoutMinutes()));
        AccessAuditRepository audits = new AccessAuditRepository();
        UserService service = new UserServiceImpl(transactions, locks,
                new AccessUserRepository(), new AccessPermissionRepository(),
                audits, new PasswordHasher(), sessions, clock);
        AuthorizationService authorization = new AuthorizationService(sessions);
        SecurityAuditHandler auditHandler = new SecurityAuditHandler(authorization,
                new SecurityAuditService(transactions, audits));
        return new UserRuntime(service, authorization,
                new RequestDeduplicator(transactions, locks), auditHandler);
    }

    private static void registerSecurityAudit(
            MessageRouter router, MessageHandler handler) {
        router.register("SECURITY_AUDIT_SEARCH", handler);
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

    private record UserRuntime(UserService service, AuthorizationService authorization,
                               RequestDeduplicator deduplicator,
                               SecurityAuditHandler auditHandler) {
    }
}
