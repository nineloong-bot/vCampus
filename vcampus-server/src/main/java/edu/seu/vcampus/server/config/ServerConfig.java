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
        int maxConnections,
        int workerThreads,
        Path databasePath,
        Path databaseResourceRoot,
        boolean databaseCreateIfMissing,
        int sessionTimeoutMinutes,
        int inventoryReservationMinutes,
        int dedupRetentionHours) {

    /** Retains the course runtime's compact programmatic configuration constructor. */
    public ServerConfig(int port, int maxConnections, int workerThreads, Path databasePath,
                        int sessionTimeoutMinutes, int inventoryReservationMinutes,
                        int dedupRetentionHours) {
        this(port, maxConnections, workerThreads, databasePath, databasePath.getParent(), false,
                sessionTimeoutMinutes, inventoryReservationMinutes, dedupRetentionHours);
    }

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
        int maxConnections = integer(properties, "server.maxConnections", 1, 10_000);
        int workerThreads = integer(properties, "server.workerThreads", 1, 1_024);
        int sessionTimeout = integer(properties, "session.timeoutMinutes", 1, 1_440);
        int reservation = integer(properties, "inventory.reservationMinutes", 1, 1_440);
        int retention = integer(properties, "dedup.retentionHours", 1, 720);
        String database = required(properties, "database.path");
        Path databasePath = baseDirectory.resolve(database).normalize().toAbsolutePath();
        boolean createIfMissing = Boolean.parseBoolean(
                properties.getProperty("database.createIfMissing", "false").trim());
        if (!createIfMissing && !Files.isRegularFile(databasePath)) {
            throw new ConfigurationException(
                    "database.path 指向的 Access 数据库不存在: " + databasePath);
        }
        String resourceRoot = properties.getProperty("database.resourceRoot", "").trim();
        Path databaseResourceRoot = resourceRoot.isEmpty()
                ? databasePath.getParent()
                : baseDirectory.resolve(resourceRoot).normalize().toAbsolutePath();
        if (!Files.isDirectory(databaseResourceRoot)) {
            throw new ConfigurationException(
                    "database.resourceRoot 不存在或不是目录: " + databaseResourceRoot);
        }
        return new ServerConfig(port, maxConnections, workerThreads, databasePath,
                databaseResourceRoot, createIfMissing,
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
