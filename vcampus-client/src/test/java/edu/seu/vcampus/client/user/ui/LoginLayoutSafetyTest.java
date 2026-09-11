package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.user.service.UserClientService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LoginLayoutSafetyTest {
    @ParameterizedTest @CsvSource({"880,620", "800,560"})
    void demoTextAndControlsFitInsideBothSupportedWindowSizes(int width, int height)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var frame = new LoginFrame(mock(UserClientService.class), result -> { });
            try {
                frame.setSize(width, height);
                frame.addNotify();
                frame.validate();
                assertThat(frame.getSize()).isEqualTo(new Dimension(width, height));
                frame.showNotice("登录失败次数过多，请 30 秒后再试");
                frame.validate();
                inspect(frame.getContentPane(), frame.getContentPane());
                assertThat(text(frame)).contains("SUPER_ADMIN");
                var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                frame.getContentPane().printAll(graphics);
                graphics.dispose();
                try {
                    Path output = Path.of("target", "ui-review", "login-" + width + ".png");
                    Files.createDirectories(output.getParent());
                    javax.imageio.ImageIO.write(image, "png", output.toFile());
                } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            } finally { frame.dispose(); }
        });
    }

    private static void inspect(Container parent, Container root) {
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) continue;
            Rectangle bounds = SwingUtilities.convertRectangle(parent, child.getBounds(), root);
            assertThat(new Rectangle(0, 0, root.getWidth(), root.getHeight()).contains(bounds))
                    .as("component remains in window: %s", child.getName()).isTrue();
            if (child instanceof JLabel label) {
                assertThat(label.getWidth()).as("uncropped label: %s", label.getName())
                        .isGreaterThanOrEqualTo(label.getPreferredSize().width);
                assertThat(label.getHeight()).isGreaterThanOrEqualTo(label.getPreferredSize().height);
            }
            if (child instanceof JTextField field) {
                assertThat(field.getText()).isEmpty();
                assertThat(field.getWidth()).isGreaterThan(100);
            }
            if (child instanceof Container nested) inspect(nested, root);
        }
    }

    private static String text(Container root) {
        StringBuilder result = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) result.append(label.getText());
            if (child instanceof Container nested) result.append(text(nested));
        }
        return result.toString();
    }
}
