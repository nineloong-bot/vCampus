package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiThemeInstaller;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.course.service.CourseClientException;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.paging.PageResult;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Deterministic 1280x800 visual-review fixture using the teammate shell tokens. */
public final class CourseUiScreenshotGenerator {
    private CourseUiScreenshotGenerator() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of("docs/ui-review/course");
        Files.createDirectories(output);
        UiThemeInstaller.install();
        JComponent[] pages = new JComponent[8];
        SwingUtilities.invokeAndWait(() -> {
            pages[0] = shell(new OfferingSearchPanel(CourseUiGateway.preview()));
            pages[1] = shell(new MySchedulePanel(CourseUiGateway.preview()));
            pages[2] = shell(new OfferingSearchPanel(gateway(new CompletableFuture<>())));
            pages[3] = shell(new OfferingSearchPanel(gateway(CompletableFuture.completedFuture(
                    new PageResult<>(List.of(), 0, 20, 0)))));
            pages[4] = shell(new OfferingSearchPanel(gateway(CompletableFuture.failedFuture(
                    new IllegalStateException("internal details")))));
            pages[5] = shell(new OfferingSearchPanel(gateway(CompletableFuture.failedFuture(
                    new CourseClientException("COMMON_NETWORK_ERROR", "socket details", null, true)))));
            pages[6] = shell(new AdjustmentPanel(CourseUiGateway.preview()));
            pages[7] = shell(new RetakePanel(CourseUiGateway.preview()));
        });
        // A second EDT turn lets completed asynchronous preview futures render.
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            try {
                capture(pages[0], output.resolve("c01-offering-search--normal.png"));
                capture(pages[1], output.resolve("c04-my-schedule--normal.png"));
                capture(pages[2], output.resolve("c01-offering-search--loading.png"));
                capture(pages[3], output.resolve("c01-offering-search--empty.png"));
                capture(pages[4], output.resolve("c01-offering-search--error.png"));
                capture(pages[5], output.resolve("c01-offering-search--disconnected.png"));
                capture(pages[6], output.resolve("c05-adjustment--normal.png"));
                capture(pages[7], output.resolve("c06-retake--normal.png"));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }

    private static CourseUiGateway gateway(CompletableFuture<PageResult<OfferingSummary>> offerings) {
        return new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return offerings; }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        };
    }

    private static JPanel shell(JComponent page) {
        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(UiColors.BACKGROUND_PAGE);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiColors.PRIMARY);
        header.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.XL, 0, UiSpacing.XL));
        header.setPreferredSize(new Dimension(0, UiDimensions.HEADER_HEIGHT));
        JLabel product = new JLabel("vCampus · 虚拟校园"); product.setFont(UiTypography.DISPLAY); product.setForeground(UiColors.TEXT_ON_PRIMARY);
        JLabel identity = new JLabel("20260001 · 学生    连接正常"); identity.setFont(UiTypography.BODY); identity.setForeground(UiColors.TEXT_ON_PRIMARY);
        header.add(product, BorderLayout.WEST); header.add(identity, BorderLayout.EAST);
        shell.add(header, BorderLayout.NORTH);

        JPanel navigation = new JPanel();
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setBackground(UiColors.BACKGROUND_NAV);
        navigation.setPreferredSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, 0));
        navigation.add(Box.createVerticalStrut(UiSpacing.LG));
        for (String text : new String[]{"学籍档案", "课程中心", "图书借阅", "校园商城", "账户设置"}) {
            JButton item = new JButton(text); item.setFont(UiTypography.BODY_BOLD);
            item.setHorizontalAlignment(JButton.LEFT); item.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.XL, 0, 0));
            item.setMaximumSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, 44));
            item.setPreferredSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, 44));
            boolean selected = text.equals("课程中心"); item.setBackground(selected ? UiColors.PRIMARY : UiColors.BACKGROUND_NAV);
            item.setForeground(selected ? UiColors.TEXT_ON_PRIMARY : UiColors.TEXT_PRIMARY); navigation.add(item);
        }
        shell.add(navigation, BorderLayout.WEST); shell.add(page, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout()); footer.setBackground(UiColors.BACKGROUND_SUBTLE);
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,UiColors.BORDER_DEFAULT), BorderFactory.createEmptyBorder(0,UiSpacing.MD,0,UiSpacing.MD)));
        footer.setPreferredSize(new Dimension(0, UiDimensions.STATUS_BAR_HEIGHT)); footer.add(new JLabel("就绪"), BorderLayout.WEST); footer.add(new JLabel("2026-08-28  15:30"), BorderLayout.EAST);
        shell.add(footer, BorderLayout.SOUTH);
        return shell;
    }

    private static void capture(JComponent component, Path target) throws Exception {
        JFrame host = new JFrame(); host.setUndecorated(true); host.setContentPane(component);
        host.setSize(UiDimensions.WINDOW_WIDTH, UiDimensions.WINDOW_HEIGHT); host.addNotify(); host.validate();
        SwingUtilities.invokeLater(() -> { });
        BufferedImage image = new BufferedImage(UiDimensions.WINDOW_WIDTH, UiDimensions.WINDOW_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); component.printAll(graphics); graphics.dispose();
        ImageIO.write(image, "png", target.toFile()); host.dispose();
    }
}
