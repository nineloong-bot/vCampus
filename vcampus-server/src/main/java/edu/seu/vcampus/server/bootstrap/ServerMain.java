package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.config.ConfigurationException;
import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;

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
        if (config.databaseCreateIfMissing()) {
            Path parent = config.databasePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        }
        String databaseUrl = "jdbc:ucanaccess://" + config.databasePath()
                + (config.databaseCreateIfMissing() ? ";newDatabaseVersion=V2010" : "");
        ConnectionProvider connections = () -> DriverManager.getConnection(databaseUrl);
        ApplicationRuntime runtime = ApplicationRuntime.create(connections,
                config.databaseResourceRoot(), Clock.systemUTC(),
                Duration.ofMinutes(config.sessionTimeoutMinutes()));
        SocketServer server = new SocketServer(config.port(), config.workerThreads(),
                config.maxConnections(), runtime.router());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(server), "vcampus-shutdown"));
        LOGGER.info("VCampus 服务端已启动，监听端口 {}", config.port());
        server.serve();
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
}
