package edu.seu.vcampus.client;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiThemeInstaller;
import edu.seu.vcampus.client.user.ui.LoginFrame;

import javax.swing.SwingUtilities;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/** Loads client configuration, connects, and opens the Swing shell. */
public final class ClientMain {
    private ClientMain() {
    }

    /** Starts the client using an optional path to client.properties. */
    public static void main(String[] args) {
        Path configFile = Path.of(args.length == 0 ? "config/client.properties" : args[0]);
        try {
            UiThemeInstaller.install();
            Properties properties = load(configFile);
            String host = required(properties, "server.host");
            int port = integer(properties, "server.port", 1, 65_535);
            int connectTimeout = integer(properties, "connection.timeoutSeconds", 1, 300);
            int requestTimeout = integer(properties, "request.timeoutSeconds", 1, 300);
            ClientConnection connection = new ClientConnection(host, port);
            connection.connect(Duration.ofSeconds(connectTimeout));
            Runtime.getRuntime().addShutdownHook(new Thread(connection::close, "vcampus-client-close"));
            SwingUtilities.invokeLater(() -> new LoginFrame(
                    connection, Duration.ofSeconds(requestTimeout)).setVisible(true));
        } catch (Exception error) {
            System.err.println("客户端启动失败：" + error.getMessage());
            System.exit(2);
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
