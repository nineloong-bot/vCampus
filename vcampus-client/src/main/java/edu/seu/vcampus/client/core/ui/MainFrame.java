package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;

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

    /** Creates the application shell with the logged-in demo identity. */
    public MainFrame(UserView user) {
        super("vCampus");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        header.add(new JLabel("vCampus"), BorderLayout.WEST);
        header.add(new ConnectionStatusPanel(), BorderLayout.EAST);
        footer.add(new JLabel("就绪"), BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        add(navigation, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        if (user != null) {
            addDemoIdentityAndPlaceholders(user);
        }
        pack();
    }

    private void addDemoIdentityAndPlaceholders(UserView user) {
        header.add(new JLabel(
                "当前用户：" + user.loginId() + "（" + user.role().name() + "）"),
                BorderLayout.CENTER);
        content.setLayout(new GridLayout(0, 1, 8, 8));
        for (String module : new String[]{"学籍", "选课", "图书馆", "商城"}) {
            content.add(new JLabel(module + "：建设中"));
        }
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
