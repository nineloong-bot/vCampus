package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Minimal layout-managed application shell extended by feature UI plans. */
public final class MainFrame extends JFrame {
    private final JPanel header = new JPanel(new BorderLayout());
    private final JPanel navigation = new JPanel();
    private final JPanel content = new JPanel();
    private final JPanel footer = new JPanel(new BorderLayout());
    private final PageNavigator pageNavigator = new PageNavigator(content);

    /** Creates the structural header, navigation, content, and footer seams. */
    public MainFrame() {
        this(null);
    }

    /** Creates the application shell for an authenticated user. */
    public MainFrame(UserView user) {
        super("vCampus");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        header.add(new JLabel("vCampus"), BorderLayout.WEST);
        if (user != null) {
            header.add(new JLabel(user.loginId() + " · " + roleName(user), JLabel.CENTER),
                    BorderLayout.CENTER);
            content.add(new JLabel("登录成功，欢迎 " + user.loginId(), JLabel.CENTER));
        }
        header.add(new ConnectionStatusPanel(user == null ? "未连接" : "连接正常"),
                BorderLayout.EAST);
        footer.add(new JLabel("就绪"), BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        add(navigation, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        pack();
        setSize(900, 600);
        setLocationRelativeTo(null);
    }

    private static String roleName(UserView user) {
        return switch (user.role()) {
            case STUDENT -> "学生";
            case TEACHER -> "教师";
            case ADMIN -> "管理员";
        };
    }

    /** Returns the header extension point. */
    public JPanel header() {
        return header;
    }

    /** Returns the navigation extension point. */
    public JPanel navigation() {
        return navigation;
    }

    /** Returns the page content extension point. */
    public JPanel content() {
        return content;
    }

    /** Returns the footer extension point. */
    public JPanel footer() {
        return footer;
    }

    /** Returns the shared card-layout page navigator. */
    public PageNavigator pageNavigator() {
        return pageNavigator;
    }
}
