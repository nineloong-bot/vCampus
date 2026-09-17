package edu.seu.vcampus.client.core.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.AWTEvent;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads and applies application brand icons across desktop platforms.
 *
 * <p>Supports macOS dock integration via {@link Taskbar}, as well as
 * Windows/Linux title bar, taskbar, and Alt-Tab window switcher icons via
 * multi-resolution {@link Window#setIconImages(List)}.</p>
 */
public final class AppIcon {
    private static final String[] ICON_RESOURCE_PATHS = {
            "/icons/app_icon.png",
            "/icons/app_icon.jpg",
            "/icons/app_icon.jpeg"
    };
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static volatile Image primaryIcon;
    private static volatile List<Image> multiResolutionIcons;
    private static volatile boolean taskbarInstalled;

    private AppIcon() {
    }

    /**
     * Installs the application icon into the platform taskbar and hooks window creation.
     */
    public static void install() {
        if (taskbarInstalled) {
            return;
        }
        taskbarInstalled = true;
        installTaskbar();
        registerGlobalWindowHook();
    }

    private static void installTaskbar() {
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    Image icon = loadIcon();
                    if (icon != null) {
                        taskbar.setIconImage(icon);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Gracefully ignore environments where Taskbar is unsupported or headless
        }
    }

    private static void registerGlobalWindowHook() {
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (event instanceof WindowEvent windowEvent
                        && windowEvent.getID() == WindowEvent.WINDOW_OPENED) {
                    Window window = windowEvent.getWindow();
                    if (window.getIconImages().isEmpty()) {
                        applyTo(window);
                    }
                }
            }, AWTEvent.WINDOW_EVENT_MASK);
        } catch (Throwable ignored) {
            // Gracefully ignore security restrictions or headless environments
        }
    }

    /**
     * Applies the multi-resolution application icons to a target window.
     *
     * @param window the window to receive the icons, ignored if null
     */
    public static void applyTo(Window window) {
        if (window == null) {
            return;
        }
        install();
        List<Image> icons = loadIconImages();
        if (!icons.isEmpty()) {
            window.setIconImages(icons);
        }
    }

    /**
     * Loads the primary high-resolution application icon.
     *
     * @return the icon image, or null if no icon resource is present
     */
    public static Image loadIcon() {
        Image cached = primaryIcon;
        if (cached != null) {
            return cached;
        }
        for (String path : ICON_RESOURCE_PATHS) {
            Image loaded = readImageResource(path);
            if (loaded != null) {
                primaryIcon = loaded;
                return loaded;
            }
        }
        return null;
    }

    /**
     * Loads a list of scaled icons suitable for high-DPI displays and platform taskbars.
     *
     * @return an unmodifiable list of icon images at various resolutions
     */
    public static List<Image> loadIconImages() {
        List<Image> cached = multiResolutionIcons;
        if (cached != null) {
            return cached;
        }
        Image base = loadIcon();
        if (base == null) {
            multiResolutionIcons = Collections.emptyList();
            return multiResolutionIcons;
        }
        List<Image> images = new ArrayList<>();
        for (int size : ICON_SIZES) {
            images.add(createScaledImage(base, size, size));
        }
        images.add(base);
        multiResolutionIcons = Collections.unmodifiableList(images);
        return multiResolutionIcons;
    }

    private static Image readImageResource(String resourcePath) {
        try {
            URL url = AppIcon.class.getResource(resourcePath);
            if (url != null) {
                try (InputStream in = url.openStream()) {
                    BufferedImage buffered = ImageIO.read(in);
                    if (buffered != null) {
                        return buffered;
                    }
                }
                return new ImageIcon(url).getImage();
            }
        } catch (Throwable ignored) {
            // Ignored to avoid startup abort on corrupted/missing image
        }
        return null;
    }

    private static Image createScaledImage(Image source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(source, 0, 0, width, height, null);
        } finally {
            g2.dispose();
        }
        return scaled;
    }
}
