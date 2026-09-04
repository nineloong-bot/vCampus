package edu.seu.vcampus.client.course.demo;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.course.ui.AdjustmentAuditPanel;
import edu.seu.vcampus.client.course.ui.AdjustmentPanel;
import edu.seu.vcampus.client.course.ui.CourseCatalogPanel;
import edu.seu.vcampus.client.course.ui.CourseUiGateway;
import edu.seu.vcampus.client.course.ui.MyEnrollmentPanel;
import edu.seu.vcampus.client.course.ui.MySchedulePanel;
import edu.seu.vcampus.client.course.ui.OfferingManagementPanel;
import edu.seu.vcampus.client.course.ui.OfferingSearchPanel;
import edu.seu.vcampus.client.course.ui.OutcomeImportPanel;
import edu.seu.vcampus.client.course.ui.RetakePanel;
import edu.seu.vcampus.client.course.ui.TermManagementPanel;
import edu.seu.vcampus.client.course.ui.SelectionPhaseManagementPanel;
import edu.seu.vcampus.client.course.ui.StudentCourseSelectionPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Reviewable shared-token shell for the real-socket course demo. */
public final class CourseDemoFrame extends JFrame {
    private final JPanel content = new JPanel(new BorderLayout());
    private final Map<JButton, Supplier<JPanel>> pages = new LinkedHashMap<>();
    private final JLabel identity = new JLabel();

    public CourseDemoFrame(CourseUiGateway gateway, String token, String role) {
        this(gateway, token, role, null);
    }

    public CourseDemoFrame(CourseUiGateway gateway, String token, String role, ClientConnection connection) {
        super("vCampus · 课程模块 Demo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(header(token, role, connection), BorderLayout.NORTH);
        add(navigation(gateway, role), BorderLayout.WEST);
        content.setBackground(UiColors.BACKGROUND_PAGE);
        add(content, BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        setMinimumSize(new Dimension(UiDimensions.WINDOW_MIN_WIDTH, UiDimensions.WINDOW_MIN_HEIGHT));
        setSize(UiDimensions.WINDOW_WIDTH, UiDimensions.WINDOW_HEIGHT);
        setLocationRelativeTo(null);
        pages.entrySet().stream().findFirst().ifPresent(entry -> show(entry.getKey(), entry.getValue()));
    }

    private JPanel header(String token, String role, ClientConnection connection) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiColors.PRIMARY);
        header.setPreferredSize(new Dimension(0, UiDimensions.HEADER_HEIGHT));
        header.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.XL, 0, UiSpacing.XL));
        JLabel product = new JLabel("vCampus · 虚拟校园");
        product.setFont(UiTypography.DISPLAY);
        product.setForeground(UiColors.TEXT_ON_PRIMARY);
        updateIdentity(token, role, connection == null ? null : connection.state());
        identity.setFont(UiTypography.BODY);
        identity.setForeground(UiColors.TEXT_ON_PRIMARY);
        if (connection != null) {
            connection.addStateListener(state -> SwingUtilities.invokeLater(() -> updateIdentity(token, role, state)));
        }
        header.add(product, BorderLayout.WEST);
        header.add(identity, BorderLayout.EAST);
        return header;
    }

    private void updateIdentity(String token, String role, ConnectionState state) {
        String connection = state == null ? "连接状态未知" : switch (state) {
            case CONNECTING -> "正在连接";
            case CONNECTED -> "连接正常";
            case FAILED -> "连接异常";
            case DISCONNECTED -> "连接断开";
        };
        identity.setText(token + " · " + roleName(role) + "    " + connection);
    }

    private JPanel navigation(CourseUiGateway gateway, String role) {
        JPanel navigation = new JPanel();
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setBackground(UiColors.BACKGROUND_NAV);
        navigation.setPreferredSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, 0));
        navigation.add(Box.createVerticalStrut(UiSpacing.LG));
        if ("ADMIN".equalsIgnoreCase(role)) {
            addPage(navigation, "选课阶段", () -> new SelectionPhaseManagementPanel(gateway));
            addPage(navigation, "学期管理", () -> new TermManagementPanel(gateway));
            addPage(navigation, "课程目录", () -> new CourseCatalogPanel(gateway));
            addPage(navigation, "教学班管理", () -> new OfferingManagementPanel(gateway));
            addPage(navigation, "结果导入", () -> new OutcomeImportPanel(gateway));
            addPage(navigation, "异动审计", () -> new AdjustmentAuditPanel(gateway));
        } else {
            addPage(navigation, "选课", () -> new StudentCourseSelectionPanel(gateway));
            addPage(navigation, "我的选课", () -> new MyEnrollmentPanel(gateway));
            addPage(navigation, "我的课表", () -> new MySchedulePanel(gateway));
        }
        navigation.add(Box.createVerticalGlue());
        return navigation;
    }

    private void addPage(JPanel navigation, String text, Supplier<JPanel> page) {
        JButton button = new JButton(text);
        button.setFont(UiTypography.BODY_BOLD);
        button.setHorizontalAlignment(JButton.LEFT);
        button.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.XL, 0, 0));
        button.setMaximumSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, UiDimensions.NAVIGATION_ITEM_HEIGHT));
        button.setPreferredSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, UiDimensions.NAVIGATION_ITEM_HEIGHT));
        button.setBackground(UiColors.BACKGROUND_NAV);
        button.setForeground(UiColors.TEXT_PRIMARY);
        button.addActionListener(event -> show(button, page));
        pages.put(button, page);
        navigation.add(button);
    }

    private void show(JButton selected, Supplier<JPanel> page) {
        pages.keySet().forEach(button -> {
            boolean active = button == selected;
            button.setBackground(active ? UiColors.PRIMARY : UiColors.BACKGROUND_NAV);
            button.setForeground(active ? UiColors.TEXT_ON_PRIMARY : UiColors.TEXT_PRIMARY);
        });
        content.removeAll();
        content.add(page.get(), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private JPanel footer() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(UiColors.BACKGROUND_SUBTLE);
        footer.setPreferredSize(new Dimension(0, UiDimensions.STATUS_BAR_HEIGHT));
        footer.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.XL, 0, UiSpacing.XL));
        JLabel status = new JLabel("课程 Demo · 页面切换时自动重新读取服务端数据");
        status.setFont(UiTypography.CAPTION);
        status.setForeground(UiColors.TEXT_SECONDARY);
        footer.add(status, BorderLayout.WEST);
        return footer;
    }

    private static String roleName(String role) { return "ADMIN".equalsIgnoreCase(role) ? "管理员" : "学生"; }
}
