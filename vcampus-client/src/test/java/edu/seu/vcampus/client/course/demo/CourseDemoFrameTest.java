package edu.seu.vcampus.client.course.demo;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.course.ui.CourseUiGateway;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.routing.MessageRouter;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CourseDemoFrameTest {
    @Test
    void headerTracksTheRealClientConnectionState() throws Exception {
        SocketServer server = new SocketServer(0, 2, 4, new MessageRouter(Map.of()));
        Thread serving = Thread.ofPlatform().start(() -> {
            try { server.serve(); } catch (Exception ignored) { }
        });
        try (ClientConnection connection = new ClientConnection("127.0.0.1", server.localPort())) {
            connection.connect(Duration.ofSeconds(5));
            AtomicReference<CourseDemoFrame> frame = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> frame.set(new CourseDemoFrame(
                    CourseUiGateway.preview(), "student-demo-1", "STUDENT", connection)));

            assertThat(labels(frame.get())).anyMatch(text -> text.contains("student-demo-1") && text.contains("连接正常"));
            connection.close();
            SwingUtilities.invokeAndWait(() -> { });
            assertThat(labels(frame.get())).anyMatch(text -> text.contains("student-demo-1") && text.contains("连接断开"));
            SwingUtilities.invokeAndWait(frame.get()::dispose);
        } finally {
            server.stopAccepting();
            server.awaitRequests(Duration.ofSeconds(5));
            server.close();
            serving.join(5_000);
        }
    }

    private static List<String> labels(Container root) {
        return descendants(root).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
                .map(JLabel::getText).toList();
    }

    private static List<Component> descendants(Container root) {
        List<Component> all = new ArrayList<>();
        for (Component child : root.getComponents()) {
            all.add(child);
            if (child instanceof Container nested) all.addAll(descendants(nested));
        }
        return all;
    }
}
