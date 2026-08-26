package edu.seu.vcampus.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Validated immutable server startup configuration. */
public record ServerConfig(
        int port,
        int logViewerPort,
        int maxConnections,
        int workerThreads,
        Path databasePath,
        int sessionTimeoutMinutes,
        int inventoryReservationMinutes,
        int dedupRetentionHours) {

    /** Loads properties from a file and resolves paths against an application base directory. */
    public static ServerConfig load(Path configFile, Path baseDirectory) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configFile)) {
            properties.load(input);
        } catch (IOException error) {
            throw new ConfigurationException("无法读取服务端配置: " + configFile, error);
        }
        return from(properties, baseDirectory);
    }

    /** Parses and strictly validates all required server properties. */
    public static ServerConfig from(Properties properties, Path baseDirectory) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(baseDirectory, "baseDirectory");
        int port = integer(properties, "server.port", 1, 65_535);
        int logViewerPort = integer(properties, "server.logViewerPort", 1, 65_535);
        if (logViewerPort == port) {
            throw new ConfigurationException("server.logViewerPort 不能与 server.port 相同");
        }
        int maxConnections = integer(properties, "server.maxConnections", 1, 10_000);
        int workerThreads = integer(properties, "server.workerThreads", 1, 1_024);
        int sessionTimeout = integer(properties, "session.timeoutMinutes", 1, 1_440);
        int reservation = integer(properties, "inventory.reservationMinutes", 1, 1_440);
        int retention = integer(properties, "dedup.retentionHours", 1, 720);
        String database = required(properties, "database.path");
        Path databasePath = baseDirectory.resolve(database).normalize().toAbsolutePath();
        return new ServerConfig(port, logViewerPort, maxConnections, workerThreads, databasePath,
                sessionTimeout, reservation, retention);
    }

    private static int integer(Properties properties, String key, int minimum, int maximum) {
        String value = required(properties, key);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new ConfigurationException(
                    key + " 必须是 " + minimum + " 到 " + maximum + " 之间的整数");
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("缺少配置项: " + key);
        }
        return value.trim();
    }
}
