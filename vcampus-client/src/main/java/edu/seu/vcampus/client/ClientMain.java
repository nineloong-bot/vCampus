package edu.seu.vcampus.client;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiThemeInstaller;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.UserUiCoordinator;
import edu.seu.vcampus.client.library.service.LibraryClientService;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

/** Loads client configuration, connects, and opens the Swing shell. */
public final class ClientMain {
    private ClientMain() {
    }

    /** Starts the client using an optional path to client.properties. */
    public static void main(String[] args) {
        Path configFile = Path.of(args.length == 0 ? "config/client.properties" : args[0]);
        try {
            Properties properties = load(configFile);
            String host = required(properties, "server.host");
            int port = integer(properties, "server.port", 1, 65_535);
            int connectTimeout = integer(properties, "connection.timeoutSeconds", 1, 300);
            ClientConnection connection = new ClientConnection(host, port);
            Duration timeout = Duration.ofSeconds(connectTimeout);
            connectForStartup(connection, timeout);
            Runtime.getRuntime().addShutdownHook(new Thread(connection::close, "vcampus-client-close"));
            UserClientService users = new UserClientService(
                    connection, UUID.randomUUID().toString(), timeout);
            LibraryClientService library = new LibraryClientService(connection, timeout);
            SwingUtilities.invokeLater(() -> {
                UiThemeInstaller.install();
                new UserUiCoordinator(users, connection, library).start();
            });
        } catch (Exception error) {
            System.err.println("客户端启动失败：" + error.getMessage());
            System.exit(2);
        }
    }

    static void connectForStartup(ClientConnection connection, Duration timeout) {
        try {
            connection.connect(timeout);
        } catch (IOException error) {
            System.err.println("服务器连接失败，可在登录页查看连接状态：" + error.getMessage());
        }
    }

    private static Properties load(Path file) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少配置项: " + key);
        }
        return value.trim();
    }

    private static int integer(Properties properties, String key, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(required(properties, key));
            if (value < minimum || value > maximum) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " 配置无效");
        }
    }
}
