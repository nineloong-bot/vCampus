package edu.seu.vcampus.client.course.demo;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiThemeInstaller;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.course.ui.CourseClientGateway;

import javax.swing.SwingUtilities;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/** Starts the real-socket course demo using a demo token and shared UI design tokens. */
public final class CourseDemoClientMain {
    private CourseDemoClientMain() { }

    public static void main(String[] args) {
        Path config = Path.of(args.length == 0 ? "config/client.properties" : args[0]);
        String token = args.length > 1 ? args[1] : "student-demo-1";
        String role = args.length > 2 ? args[2] : "STUDENT";
        try {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(config)) { properties.load(input); }
            ClientConnection connection = new ClientConnection(properties.getProperty("server.host", "127.0.0.1"),
                    Integer.parseInt(properties.getProperty("server.port", "8888")));
            connection.connect(Duration.ofSeconds(Integer.parseInt(
                    properties.getProperty("connection.timeoutSeconds", "10"))));
            connection.setSessionToken(token);
            Runtime.getRuntime().addShutdownHook(new Thread(connection::close, "course-demo-client-close"));
            CourseClientGateway gateway = new CourseClientGateway(new CourseClientService(connection));
            UiThemeInstaller.install();
            SwingUtilities.invokeLater(() -> new CourseDemoFrame(gateway, token, role).setVisible(true));
        } catch (Exception failure) {
            System.err.println("课程 Demo 客户端启动失败：" + failure.getMessage());
            System.exit(2);
        }
    }
}
