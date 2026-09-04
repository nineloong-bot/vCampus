package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.core.ui.theme.UiThemeInstaller;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.LoginFrame;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

/** Generates the three deterministic integrated-login/course visual-review artifacts. */
public final class CourseUiScreenshotGenerator {
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 800;

    private CourseUiScreenshotGenerator() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of("docs/ui-review/course");
        Files.createDirectories(output);
        UiThemeInstaller.install();

        ClientConnection previewConnection = new ClientConnection("127.0.0.1", 8888);
        LoginFrame[] login = new LoginFrame[1];
        MainFrame[] student = new MainFrame[1];
        MainFrame[] administrator = new MainFrame[1];
        OfferingEditorDialog[] administratorEditor = new OfferingEditorDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            UserClientService users = new UserClientService(
                    previewConnection, "visual-review", Duration.ofSeconds(1));
            login[0] = new LoginFrame(users, previewConnection, ignored -> { });

            UserView studentUser = new UserView(
                    "demo-student", "DEMO_STUDENT", UserRole.STUDENT,
                    AccountStatus.ACTIVE, false,
                    LocalDateTime.parse("2026-09-02T09:30:00"), 0,
                    LocalDateTime.parse("2026-08-20T08:00:00"),
                    LocalDateTime.parse("2026-09-02T09:30:00"));
            student[0] = new MainFrame(studentUser);
            student[0].installPage("course",
                    new CourseWorkspacePanel(CourseUiGateway.preview(), UserRole.STUDENT));
            student[0].pageNavigator().show("course");

            UserView adminUser = new UserView(
                    "demo-admin", "DEMO_ADMIN", UserRole.ADMIN,
                    AccountStatus.ACTIVE, false,
                    LocalDateTime.parse("2026-09-04T20:30:00"), 0,
                    LocalDateTime.parse("2026-08-20T08:00:00"),
                    LocalDateTime.parse("2026-09-04T20:30:00"));
            administrator[0] = new MainFrame(adminUser);
            administrator[0].installPage("course",
                    new CourseWorkspacePanel(CourseUiGateway.preview(), UserRole.ADMIN));
            administrator[0].pageNavigator().show("course");

            administratorEditor[0] = new OfferingEditorDialog(
                    null, CourseUiGateway.preview(), null, () -> { });
            administratorEditor[0].pack();
        });

        // Completed preview futures publish their UI state through invokeLater.
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> descendants(administrator[0]).stream()
                .filter(javax.swing.JTabbedPane.class::isInstance)
                .map(javax.swing.JTabbedPane.class::cast)
                .filter(tabs -> tabs.indexOfTab("选课阶段") >= 0)
                .findFirst().ifPresent(tabs -> tabs.setSelectedIndex(tabs.indexOfTab("选课阶段"))));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> descendants(administrator[0]).stream()
                .filter(javax.swing.JTable.class::isInstance).map(javax.swing.JTable.class::cast)
                .filter(table -> table.getRowCount() > 0).findFirst()
                .ifPresent(table -> table.setRowSelectionInterval(0, 0)));
        SwingUtilities.invokeAndWait(() -> descendants(student[0]).stream()
                .filter(javax.swing.JButton.class::isInstance).map(javax.swing.JButton.class::cast)
                .filter(button -> button.getText().startsWith("▶  MATH101"))
                .findFirst().ifPresent(javax.swing.JButton::doClick));
        SwingUtilities.invokeAndWait(() -> {
            try {
                capture(login[0], output.resolve("integrated-login.png"),
                        WINDOW_WIDTH, WINDOW_HEIGHT);
                capture(student[0], output.resolve("integrated-student-course.png"),
                        WINDOW_WIDTH, WINDOW_HEIGHT);
                capture(administrator[0], output.resolve("integrated-admin-selection-phase.png"),
                        WINDOW_WIDTH, WINDOW_HEIGHT);
                Dimension editorSize = administratorEditor[0].getSize();
                capture(administratorEditor[0],
                        output.resolve("integrated-admin-offering-editor.png"),
                        editorSize.width, editorSize.height);
            } catch (Exception error) {
                throw new IllegalStateException("Could not generate integrated UI screenshots", error);
            } finally {
                login[0].dispose();
                student[0].dispose();
                administrator[0].dispose();
                administratorEditor[0].dispose();
                previewConnection.close();
            }
        });
    }

    private static java.util.List<java.awt.Component> descendants(java.awt.Container root) {
        java.util.List<java.awt.Component> found = new java.util.ArrayList<>();
        for (java.awt.Component child : root.getComponents()) {
            found.add(child);
            if (child instanceof java.awt.Container nested) found.addAll(descendants(nested));
        }
        return found;
    }

    private static void capture(java.awt.Window window, Path target, int width, int height)
            throws Exception {
        window.setSize(width, height);
        window.addNotify();
        window.validate();
        JComponent content = (JComponent) ((RootPaneContainer) window).getContentPane();
        content.setSize(width, height);
        content.doLayout();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            content.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", target.toFile());
    }
}
