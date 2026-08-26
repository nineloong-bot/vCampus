package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.client.core.ui.shell.ApplicationStatusBar;
import edu.seu.vcampus.client.core.ui.shell.IdentityHeader;
import edu.seu.vcampus.client.core.ui.shell.PermissionNavigation;
import edu.seu.vcampus.client.core.ui.template.ModulePlaceholderPage;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

/** Reviewed application shell shown after a successful login. */
public final class MainFrame extends JFrame {
    private static final List<ModuleDefinition> MODULES = List.of(
            new ModuleDefinition("student", "学籍档案", "查看与维护个人学籍信息",
                    "基本资料、联系方式、学籍状态与组织信息"),
            new ModuleDefinition("course", "课程中心", "浏览课程并管理选课安排",
                    "课程检索、教学班、选课与个人课表"),
            new ModuleDefinition("library", "图书借阅", "检索馆藏并管理个人借阅",
                    "馆藏检索、当前借阅、续借与借阅历史"),
            new ModuleDefinition("shop", "校园商城", "浏览校园商品并管理订单",
                    "商品检索、购物车、结算与订单"),
            new ModuleDefinition("account", "账户设置", "维护账户与登录安全",
                    "账户资料、修改密码与退出登录"));

    private final JPanel header;
    private final PermissionNavigation navigation;
    private final JPanel content = new JPanel();
    private final JPanel footer;
    private final PageNavigator pageNavigator = new PageNavigator(content);
    private String currentPageTitle;

    /** Creates a shell without an authenticated identity for structural tests. */
    public MainFrame() {
        this(null);
    }

    /** Creates the application shell for an authenticated user. */
    public MainFrame(UserView user) {
        super("vCampus · 虚拟校园");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        header = new IdentityHeader(user);
        navigation = new PermissionNavigation(this::displayPage);
        footer = new ApplicationStatusBar();
        content.setBackground(UiColors.BACKGROUND_PAGE);

        for (ModuleDefinition module : MODULES) {
            pageNavigator.register(module.id(), new ModulePlaceholderPage(
                    module.title(), module.description(), module.scope()));
            navigation.addEntry(module.id(), module.title());
        }

        add(header, BorderLayout.NORTH);
        add(navigation, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(UiDimensions.WINDOW_WIDTH, UiDimensions.WINDOW_HEIGHT));
        setMinimumSize(new Dimension(UiDimensions.WINDOW_MIN_WIDTH, UiDimensions.WINDOW_MIN_HEIGHT));
        pack();
        setSize(UiDimensions.WINDOW_WIDTH, UiDimensions.WINDOW_HEIGHT);
        navigation.select(MODULES.getFirst().id());
        setLocationRelativeTo(null);
    }

    private void displayPage(String pageId) {
        ModuleDefinition module = MODULES.stream()
                .filter(candidate -> candidate.id().equals(pageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown page id: " + pageId));
        currentPageTitle = module.title();
        pageNavigator.show(pageId);
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

    /** Returns the visible first-level navigation labels in review order. */
    public List<String> navigationLabels() {
        return navigation.labels();
    }

    /** Returns the selected page title. */
    public String currentPageTitle() {
        return currentPageTitle;
    }

    /** Selects a page through the same path as a navigation click. */
    public void showPage(String pageId) {
        navigation.select(pageId);
    }

    private record ModuleDefinition(String id, String title, String description, String scope) {
    }
}
