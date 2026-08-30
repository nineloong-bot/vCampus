package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.UpdateContactDialog;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.StudentView;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Conditional, display-backed visual-QA entry point. Run with a desktop display:
 * {@code mvn -pl vcampus-client -DskipTests test-compile} followed by this class
 * from the test classpath. PNGs are deliberately written only below target/visual-qa.
 */
public final class StudentVisualQaHarness {
    private StudentVisualQaHarness() { }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("A graphical display is required for visual QA");
        }
        Path output = Path.of("vcampus-client", "target", "visual-qa");
        Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> render(output));
    }

    private static void render(Path output) {
        StudentView profile = profile();
        StudentClientService students = new StudentClientService(new StudentRequestClient() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                return CompletableFuture.completedFuture((ResponseBody<T>) ResponseBody.success(profile));
            }
        }, Duration.ofSeconds(1));
        ClientConnection connection = new ClientConnection("localhost", 1);
        connected(connection);
        MainFrame frame = new MainFrame(user(), connection, students);
        try {
            paint(frame, new Dimension(1280, 800), output.resolve("student-profile-1280x800.png"));
            paint(frame, new Dimension(1024, 680), output.resolve("student-profile-1024x680.png"));
            UpdateContactDialog dialog = new UpdateContactDialog(frame, students, profile, ignored -> { });
            try {
                dialog.addNotify();
                paint(dialog, new Dimension(560, 360), output.resolve("student-contact-560x360.png"));
            } finally {
                dialog.dispose();
            }
        } finally {
            frame.dispose();
            connection.close();
        }
    }

    private static void paint(java.awt.Window window, Dimension size, Path target) {
        if (!window.isDisplayable()) window.addNotify();
        window.setSize(size);
        window.doLayout();
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            window.paintAll(graphics);
            ImageIO.write(image, "png", target.toFile());
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write " + target, failure);
        } finally {
            graphics.dispose();
        }
    }

    private static UserView user() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 9, 0);
        return new UserView("user-1", "STUDENT_01", UserRole.STUDENT,
                edu.seu.vcampus.common.user.AccountStatus.ACTIVE, false, now, 1, now, now);
    }

    private static StudentView profile() {
        return new StudentView("student-1", "user-1", "213240001", "09024101",
                StudentType.UNDERGRADUATE, "张三", "MALE", "zhangsan@seu.edu.cn",
                "13800000000", "major-1", "class-1", LocalDate.of(2024, 9, 1),
                StudentStatus.ACTIVE, 1);
    }

    private static void connected(ClientConnection connection) {
        try {
            Field state = ClientConnection.class.getDeclaredField("state");
            state.setAccessible(true);
            state.set(connection, ConnectionState.CONNECTED);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to prepare visual-QA connection", failure);
        }
    }
}
