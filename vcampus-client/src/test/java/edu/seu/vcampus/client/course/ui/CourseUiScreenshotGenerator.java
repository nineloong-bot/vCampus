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
        JComponent[] pages = new JComponent[16];
        CourseEditorDialog[] dialogs = new CourseEditorDialog[1];
        OfferingDetailDialog[] offeringDialogs = new OfferingDetailDialog[1];
        TermEditorDialog[] termDialogs = new TermEditorDialog[1];
        OfferingEditorDialog[] offeringEditorDialogs = new OfferingEditorDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            pages[0] = shell(new OfferingSearchPanel(CourseUiGateway.preview()));
            pages[1] = shell(new MySchedulePanel(CourseUiGateway.preview()));
            pages[2] = shell(new OfferingSearchPanel(gateway(new CompletableFuture<>())));
            pages[3] = shell(new OfferingSearchPanel(gateway(CompletableFuture.completedFuture(
                    new PageResult<>(List.of(), 0, 20, 0)))));
            pages[4] = shell(new OfferingSearchPanel(gateway(CompletableFuture.failedFuture(
                    new IllegalStateException("internal details")))));
            pages[5] = shell(new OfferingSearchPanel(gateway(CompletableFuture.failedFuture(
                    new CourseClientException("COMMON_NETWORK_ERROR", "socket details", null, true)))), false);
            pages[6] = shell(new AdjustmentPanel(CourseUiGateway.preview()));
            pages[7] = shell(new RetakePanel(CourseUiGateway.preview()));
            pages[8] = shell(new TermManagementPanel(CourseUiGateway.preview()));
            pages[9] = shell(new CourseCatalogPanel(CourseUiGateway.preview()));
            pages[10] = shell(new OfferingManagementPanel(CourseUiGateway.preview()));
            pages[11] = shell(new OutcomeImportPanel(CourseUiGateway.preview()));
            pages[12] = shell(new AdjustmentAuditPanel(CourseUiGateway.preview()));
            pages[13] = shell(new MyEnrollmentPanel(CourseUiGateway.preview()));
            pages[14] = shell(new MySchedulePanel(scheduleGateway(CompletableFuture.completedFuture(List.of()))));
            pages[15] = shell(new MySchedulePanel(scheduleGateway(CompletableFuture.failedFuture(
                    new CourseClientException("COMMON_NETWORK_ERROR", "socket details", null, true)))), false);
            dialogs[0] = new CourseEditorDialog(null, CourseUiGateway.preview(), null, () -> { });
            List<OfferingSummary> previewOfferings = CourseUiGateway.preview()
                    .searchOfferings(new OfferingSearchQuery("2026-autumn", "", null, true, 0, 20)).join().items();
            OfferingSummary source = previewOfferings.get(0);
            OfferingSummary target = alternateClass(source);
            offeringDialogs[0] = new OfferingDetailDialog(null, source, target,
                    "未发现时间冲突（服务端提交时将再次校验）", () -> { });
            termDialogs[0] = new TermEditorDialog(null, CourseUiGateway.preview(), null, () -> { });
            offeringEditorDialogs[0] = new OfferingEditorDialog(null, CourseUiGateway.preview(), source, () -> { });
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
                capture(pages[8], output.resolve("c07-term-management--normal.png"));
                capture(pages[9], output.resolve("c08-course-catalog--normal.png"));
                capture(pages[10], output.resolve("c09-offering-management--normal.png"));
                capture(pages[11], output.resolve("c10-outcome-import--normal.png"));
                capture(pages[12], output.resolve("c11-adjustment-audit--normal.png"));
                capture(pages[13], output.resolve("c03-my-enrollment--normal.png"));
                capture(pages[14], output.resolve("c04-my-schedule--empty.png"));
                capture(pages[15], output.resolve("c04-my-schedule--disconnected.png"));
                capture(pages[0], output.resolve("c01-offering-search--1024x680.png"), 1024, 680);
                capture(pages[1], output.resolve("c04-my-schedule--1024x680.png"), 1024, 680);
                capture(pages[6], output.resolve("c05-adjustment--1024x680.png"), 1024, 680);
                capture(pages[8], output.resolve("c07-term-management--1024x680.png"), 1024, 680);
                captureScaled(pages[0], output.resolve("c01-offering-search--150pct.png"), 1024, 680, 1.5);
                captureScaled(pages[1], output.resolve("c04-my-schedule--150pct.png"), 1024, 680, 1.5);
                captureScaled(pages[6], output.resolve("c05-adjustment--150pct.png"), 1024, 680, 1.5);
                captureScaled(pages[8], output.resolve("c07-term-management--150pct.png"), 1024, 680, 1.5);
                capture((JComponent) dialogs[0].getContentPane(), output.resolve("c08-course-editor--create.png"), 560, 620);
                dialogs[0].dispose();
                capture((JComponent) offeringDialogs[0].getContentPane(), output.resolve("c02-offering-change--confirm.png"), 720, 460);
                offeringDialogs[0].dispose();
                capture((JComponent) termDialogs[0].getContentPane(), output.resolve("c07-term-editor--create.png"), 640, 720);
                termDialogs[0].dispose();
                capture((JComponent) offeringEditorDialogs[0].getContentPane(), output.resolve("c09-offering-editor--edit.png"), 680, 690);
                offeringEditorDialogs[0].dispose();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }

    private static OfferingSummary alternateClass(OfferingSummary source) {
        ScheduleItem schedule = new ScheduleItem("s1b", "o1b", source.courseCode(), source.courseName(),
                "02班", "赵老师", "TUESDAY", 3, 4, 1, 16, "教一-203");
        return new OfferingSummary("o1b", source.termId(), source.courseId(), source.courseCode(),
                source.courseName(), "赵老师", "02班", 40, 31, "OPEN", 0, List.of(schedule));
    }

    private static CourseUiGateway gateway(CompletableFuture<PageResult<OfferingSummary>> offerings) {
        return new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return offerings; }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        };
    }

    private static CourseUiGateway scheduleGateway(CompletableFuture<List<ScheduleItem>> schedule) {
        CourseUiGateway base = CourseUiGateway.preview();
        return new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                return base.searchOfferings(query);
            }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return base.currentEnrollments();
            }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return schedule; }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return base.enroll(command); }
        };
    }

    private static JPanel shell(JComponent page) {
        return shell(page, true);
    }

    private static JPanel shell(JComponent page, boolean connected) {
        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(UiColors.BACKGROUND_PAGE);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiColors.PRIMARY);
        header.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.XL, 0, UiSpacing.XL));
        header.setPreferredSize(new Dimension(0, UiDimensions.HEADER_HEIGHT));
        JLabel product = new JLabel("vCampus · 虚拟校园"); product.setFont(UiTypography.DISPLAY); product.setForeground(UiColors.TEXT_ON_PRIMARY);
        JLabel identity = new JLabel("20260001 · 学生    " + (connected ? "连接正常" : "连接断开")); identity.setFont(UiTypography.BODY); identity.setForeground(UiColors.TEXT_ON_PRIMARY);
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
        capture(component, target, UiDimensions.WINDOW_WIDTH, UiDimensions.WINDOW_HEIGHT);
    }

    private static void capture(JComponent component, Path target, int width, int height) throws Exception {
        JFrame host = new JFrame(); host.setUndecorated(true); host.setContentPane(component);
        host.setSize(width, height); host.addNotify(); host.validate();
        SwingUtilities.invokeLater(() -> { });
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); component.printAll(graphics); graphics.dispose();
        ImageIO.write(image, "png", target.toFile()); host.dispose();
    }

    private static void captureScaled(JComponent component, Path target, int logicalWidth,
                                      int logicalHeight, double scale) throws Exception {
        JFrame host = new JFrame(); host.setUndecorated(true); host.setContentPane(component);
        host.setSize(logicalWidth, logicalHeight); host.addNotify(); host.validate();
        int pixelWidth = (int) Math.round(logicalWidth * scale);
        int pixelHeight = (int) Math.round(logicalHeight * scale);
        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(scale, scale); component.printAll(graphics); graphics.dispose();
        ImageIO.write(image, "png", target.toFile()); host.dispose();
    }
}
