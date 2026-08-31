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
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
        render(output);
    }

    private static void render(Path output) throws Exception {
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
        CountDownLatch profileLoaded = new CountDownLatch(1);
        AtomicReference<MainFrame> frameReference = new AtomicReference<>();
        try {
            onEdt(() -> {
                MainFrame frame = new MainFrame(user(), connection, students);
                JLabel email = component(frame.content(), "student.profile.email", JLabel.class);
                email.addPropertyChangeListener("text", event -> {
                    if (profile.email().equals(email.getText())) profileLoaded.countDown();
                });
                frame.setSize(1280, 800);
                frame.setVisible(true);
                frame.validate();
                frameReference.set(frame);
            });
            await(profileLoaded, "student profile render");
            MainFrame frame = frameReference.get();
            onEdt(() -> paint(frame, new Dimension(1280, 800),
                    output.resolve("student-profile-1280x800.png")));
            onEdt(() -> paint(frame, new Dimension(1024, 680),
                    output.resolve("student-profile-1024x680.png")));

            CountDownLatch dialogVisible = new CountDownLatch(1);
            CountDownLatch dialogEmailFocused = new CountDownLatch(1);
            AtomicReference<UpdateContactDialog> dialogReference = new AtomicReference<>();
            SwingUtilities.invokeLater(() -> {
                UpdateContactDialog dialog = new UpdateContactDialog(frame, students, profile, ignored -> { });
                JTextField email = component(dialog, "student.contact.email", JTextField.class);
                email.addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent event) { dialogEmailFocused.countDown(); }
                });
                dialog.addWindowListener(new WindowAdapter() {
                    @Override public void windowOpened(WindowEvent event) { dialogVisible.countDown(); }
                });
                dialogReference.set(dialog);
                dialog.setVisible(true);
            });
            await(dialogVisible, "contact dialog visibility");
            await(dialogEmailFocused, "contact email focus");
            UpdateContactDialog dialog = dialogReference.get();
            try {
                onEdt(() -> {
                    if (!component(dialog, "student.contact.email", JTextField.class).isFocusOwner()) {
                        throw new IllegalStateException("Contact email did not receive initial focus");
                    }
                });
                onEdt(() -> paint(dialog, new Dimension(560, 360),
                        output.resolve("student-contact-560x360.png")));
            } finally {
                onEdt(dialog::dispose);
            }
        } finally {
            MainFrame frame = frameReference.get();
            if (frame != null) onEdt(frame::dispose);
            connection.close();
        }
    }

    private static void paint(Window window, Dimension size, Path target) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Visual-QA capture must run on the EDT");
        }
        if (!window.isShowing()) throw new IllegalStateException("Window is not visible: " + target);
        window.setSize(size);
        window.validate();
        window.doLayout();
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            window.paintAll(graphics);
            if (!ImageIO.write(image, "png", target.toFile())) {
                throw new IllegalStateException("No PNG writer available for " + target);
            }
            BufferedImage written = ImageIO.read(target.toFile());
            if (written == null) throw new IllegalStateException("Unreadable PNG: " + target);
            validateImage(written, size, target);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write " + target, failure);
        } finally {
            graphics.dispose();
        }
    }

    private static void validateImage(BufferedImage image, Dimension size, Path target) {
        if (image.getWidth() != size.width || image.getHeight() != size.height) {
            throw new IllegalStateException("Wrong PNG dimensions for " + target);
        }
        int firstColor = 0;
        boolean sawNonTransparent = false;
        boolean sawVariation = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y);
                if ((color >>> 24) == 0) continue;
                if (!sawNonTransparent) {
                    sawNonTransparent = true;
                    firstColor = color;
                } else if (color != firstColor) {
                    sawVariation = true;
                }
            }
        }
        if (!sawNonTransparent || !sawVariation) {
            throw new IllegalStateException("Blank or uniform visual-QA image: " + target);
        }
    }

    private static void await(CountDownLatch signal, String activity) throws InterruptedException {
        if (!signal.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for " + activity);
        }
    }

    private static void onEdt(ThrowingRunnable action) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                action.run();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
    }

    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        if (name.equals(root.getName()) && type.isInstance(root)) return type.cast(root);
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                try {
                    return component(nested, name, type);
                } catch (IllegalArgumentException ignored) {
                    // Search remaining siblings.
                }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

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
