package edu.seu.vcampus.client.core.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import java.awt.Image;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests application icon resolution and frame integration. */
class AppIconTest {

    @Test
    void loadIconReturnsValidImage() {
        Image image = AppIcon.loadIcon();
        assertThat(image).isNotNull();
        assertThat(image.getWidth(null)).isPositive();
        assertThat(image.getHeight(null)).isPositive();
    }

    @Test
    void loadIconImagesProducesMultipleSizesForHiDpiAndWindows() {
        List<Image> images = AppIcon.loadIconImages();
        assertThat(images).isNotEmpty();
        assertThat(images.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void applyToSetsIconImagesOnWindow() {
        JFrame frame = new JFrame();
        try {
            AppIcon.applyTo(frame);
            assertThat(frame.getIconImages()).isNotEmpty();
        } finally {
            frame.dispose();
        }
    }

    @Test
    void installRunsSafely() {
        AppIcon.install();
    }
}
