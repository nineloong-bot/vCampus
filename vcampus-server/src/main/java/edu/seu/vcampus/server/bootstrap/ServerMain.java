package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.config.ConfigurationException;
import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.monitoring.LogViewerServer;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.user.AccessDatabase;
import edu.seu.vcampus.server.user.LoginService;
import edu.seu.vcampus.server.user.AccountManagementService;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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
        AccessDatabase database = new AccessDatabase(config.databasePath());
        database.initialize();
        SessionRegistry sessions = new SessionRegistry();
        LoginService loginService = new LoginService(database, sessions);
        AccountManagementService accountService = new AccountManagementService(database, sessions);
        MessageRouter router = new MessageRouter(Map.of(
                "PING", (request, context) -> ResponseBody.success(EmptyResponse.INSTANCE),
                "USER_LOGIN", loginService::login,
                "USER_REGISTER", accountService::register,
                "USER_LOGOUT", accountService::logout,
                "USER_GET_CURRENT", accountService::current,
                "USER_CHANGE_PASSWORD", accountService::changePassword,
                "USER_SEARCH", accountService::search));
        SocketServer server = new SocketServer(config.port(), config.workerThreads(),
                config.maxConnections(), router);
        LogViewerServer logViewer = new LogViewerServer(
                config.logViewerPort(), Path.of("logs"));
        logViewer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> shutdown(server, logViewer), "vcampus-shutdown"));
        LOGGER.info("VCampus 服务端已启动，监听端口 {}", config.port());
        LOGGER.info("VCampus 日志页面：http://127.0.0.1:{}/", config.logViewerPort());
        server.serve();
    }

    private static void shutdown(SocketServer server, LogViewerServer logViewer) {
        LOGGER.info("VCampus 服务端开始停止");
        logViewer.close();
        try {
            server.stopAccepting();
            if (!server.awaitRequests(Duration.ofSeconds(30))) {
                LOGGER.warn("等待中的请求超过 30 秒，将中止剩余任务");
            }
            server.close();
            LOGGER.info("VCampus 服务端已停止");
        } catch (Exception error) {
            LOGGER.warn("服务端停机清理未完全成功", error);
        }
    }
}
