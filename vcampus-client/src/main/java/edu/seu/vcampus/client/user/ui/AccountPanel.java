package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Safe current-account detail page and permission-gated administrator workspace. */
public final class AccountPanel extends JPanel {
    private static final String DETAIL = "detail";
    private static final Border ACTION_BORDER = BorderFactory.createEmptyBorder(6, 12, 6, 12);
    private final UserClientService users;
    private final Runnable onSessionEnded;
    private final Runnable onLoggedOut;
    private final Runnable onSessionEnding;
    private final AtomicBoolean logoutStarted = new AtomicBoolean();
    private final JPanel cards = new JPanel(new CardLayout());
    private final JPanel detail = new JPanel(new GridLayout(0, 2,
            UiSpacing.SPACE_4, UiSpacing.SPACE_3));
    private final JLabel state = new JLabel("正在加载账户信息…");

    /** Creates the account page from the login snapshot and current permissions. */
    public AccountPanel(UserClientService users, UserView signedInUser,
                        Set<String> permissions, Runnable onSessionEnded) {
        this(users, signedInUser, permissions, onSessionEnded, onSessionEnded);
    }

    /** Creates the account page with separate password-change and logout handoffs. */
    public AccountPanel(UserClientService users, UserView signedInUser,
                        Set<String> permissions, Runnable onSessionEnded,
                        Runnable onLoggedOut) {
        this(users, signedInUser, permissions, onSessionEnded, onLoggedOut, () -> { });
    }

    AccountPanel(UserClientService users, UserView signedInUser,
                 Set<String> permissions, Runnable onSessionEnded,
                 Runnable onLoggedOut, Runnable onSessionEnding) {
        super(new BorderLayout(0, UiSpacing.SPACE_4));
        this.users = Objects.requireNonNull(users, "users");
        this.onSessionEnded = Objects.requireNonNull(onSessionEnded, "onSessionEnded");
        this.onLoggedOut = Objects.requireNonNull(onLoggedOut, "onLoggedOut");
        this.onSessionEnding = Objects.requireNonNull(onSessionEnding, "onSessionEnding");
        setName("page.account"); setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        boolean administrator = signedInUser.role() == UserRole.ADMIN;
        add(heading(permissions, administrator), BorderLayout.NORTH);
        JPanel detailPage = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        detailPage.setOpaque(false); detail.setOpaque(false);
        detailPage.add(detail, BorderLayout.NORTH); detailPage.add(state, BorderLayout.SOUTH);
        cards.setOpaque(false); cards.add(detailPage, DETAIL);
        if (administrator && permissions.contains("USER_READ_ALL")) {
            cards.add(new UserManagementPanel(users, permissions), "users");
        }
        if (administrator && permissions.contains("USER_AUDIT_READ")) {
            cards.add(new SecurityAuditPanel(users), "audit");
        }
        add(cards, BorderLayout.CENTER);
        show(signedInUser);
        refresh();
    }

    private JPanel heading(Set<String> permissions, boolean administrator) {
        JPanel panel = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        panel.setOpaque(false);
        JPanel titles = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_2));
        titles.setOpaque(false);
        JLabel breadcrumb = new JLabel("虚拟校园 / 账户设置");
        breadcrumb.setFont(UiTypography.CAPTION);
        breadcrumb.setForeground(UiColors.TEXT_SECONDARY);
        JLabel title = new JLabel("账户设置"); title.setFont(UiTypography.PAGE_TITLE);
        titles.add(breadcrumb); titles.add(title); panel.add(titles, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        actions.setOpaque(false);
        ButtonGroup pages = new ButtonGroup();
        JToggleButton detailButton = button("个人账户", "account.detail");
        pages.add(detailButton); detailButton.setSelected(true); applyStyle(detailButton);
        detailButton.addActionListener(event -> select(DETAIL)); actions.add(detailButton);
        JToggleButton password = button("修改密码", "account.password");
        password.addActionListener(event -> {
            JToggleButton previous = selectedPage(pages);
            pages.clearSelection();
            password.setSelected(true);
            refreshStyles(actions);
            openPassword();
            password.setSelected(false);
            previous.setSelected(true);
            refreshStyles(actions);
        }); actions.add(password);
        if (administrator && permissions.contains("USER_READ_ALL")) {
            JToggleButton management = button("账户管理", "account.users");
            pages.add(management);
            management.addActionListener(event -> select("users")); actions.add(management);
        }
        if (administrator && permissions.contains("USER_AUDIT_READ")) {
            JToggleButton audits = button("安全审计", "account.audit");
            pages.add(audits);
            audits.addActionListener(event -> select("audit")); actions.add(audits);
        }
        for (java.awt.Component component : actions.getComponents()) {
            if (component instanceof JToggleButton button) {
                button.addActionListener(event -> refreshStyles(actions));
            }
        }
        JButton logout = logoutButton();
        logout.addActionListener(event -> confirmLogout(logout));
        actions.add(logout);
        panel.add(actions, BorderLayout.EAST); return panel;
    }

    private void refresh() {
        CompletableFuture<UserView> response;
        try { response = users.getCurrentUser(); }
        catch (RuntimeException failure) { response = CompletableFuture.failedFuture(failure); }
        if (response == null) {
            state.setText("显示登录时账户信息");
            return;
        }
        response.whenComplete((user, failure) -> onEdt(() -> {
            if (failure != null || user == null) {
                state.setText(UserErrorMessages.operation(failure, "账户信息加载失败，请重试"));
            } else {
                show(user); state.setText("账户信息已更新");
            }
        }));
    }

    private void show(UserView user) {
        detail.removeAll();
        addDetail("登录标识", user.loginId());
        addDetail("角色", role(user.role()));
        addDetail("账户状态", status(user.accountStatus()));
        addDetail("最近登录", user.lastLoginAt() == null ? "尚未登录" : user.lastLoginAt().toString());
        detail.revalidate(); detail.repaint();
    }

    private void addDetail(String name, String value) {
        JLabel label = new JLabel(name); label.setForeground(UiColors.TEXT_SECONDARY);
        detail.add(label); detail.add(new JLabel(value));
    }
    private void openPassword() {
        ChangePasswordDialog dialog = new ChangePasswordDialog(
                SwingUtilities.getWindowAncestor(this), users, onSessionEnding, onSessionEnded);
        dialog.setVisible(true);
    }
    private void confirmLogout(JButton button) {
        LogoutConfirmationDialog dialog = new LogoutConfirmationDialog(
                SwingUtilities.getWindowAncestor(this), () -> logout(button));
        dialog.setVisible(true);
    }
    private void logout(JButton button) {
        if (!logoutStarted.compareAndSet(false, true)) return;
        onSessionEnding.run();
        button.setEnabled(false);
        button.setText("正在退出…");
        CompletableFuture<Void> response;
        try { response = users.logout(); }
        catch (RuntimeException failure) { response = CompletableFuture.failedFuture(failure); }
        if (response == null) response = CompletableFuture.failedFuture(
                new IllegalStateException("Logout did not return a result"));
        response.whenComplete((ignored, failure) -> onEdt(this::finishLogout));
    }
    private void finishLogout() {
        onLoggedOut.run();
    }
    private void select(String card) { ((CardLayout) cards.getLayout()).show(cards, card); }
    private static JButton logoutButton() {
        JButton button = new JButton("退出登录");
        button.setUI(new BasicButtonUI());
        button.setName("account.logout");
        button.getAccessibleContext().setAccessibleName("退出登录");
        button.setBackground(UiColors.BACKGROUND_SUBTLE);
        button.setForeground(UiColors.ERROR_FG);
        button.setFocusPainted(false);
        button.setBorder(ACTION_BORDER);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        return button;
    }
    private static JToggleButton button(String text, String name) {
        JToggleButton button = new JToggleButton(text); button.setUI(new BasicToggleButtonUI());
        button.setName(name); button.getAccessibleContext().setAccessibleName(text);
        button.setFocusPainted(false); button.setBorder(ACTION_BORDER);
        button.setOpaque(true); button.setContentAreaFilled(true);
        button.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { applyFocusBorder(button); }
            @Override public void focusLost(FocusEvent event) { button.setBorder(ACTION_BORDER); }
        });
        applyStyle(button); return button;
    }
    private static void refreshStyles(JPanel actions) {
        for (java.awt.Component component : actions.getComponents()) {
            if (component instanceof JToggleButton button) applyStyle(button);
        }
    }
    private static void applyStyle(JToggleButton button) {
        button.setBackground(button.isSelected() ? UiColors.PRIMARY : UiColors.BACKGROUND_SUBTLE);
        button.setForeground(button.isSelected()
                ? UiColors.TEXT_ON_PRIMARY : UiColors.TEXT_PRIMARY);
    }
    private static void applyFocusBorder(JToggleButton button) {
        java.awt.Color color = button.isSelected()
                ? UiColors.TEXT_ON_PRIMARY : UiColors.PRIMARY;
        button.setBorder(new CompoundBorder(BorderFactory.createLineBorder(color),
                BorderFactory.createEmptyBorder(5, 11, 5, 11)));
    }
    private static JToggleButton selectedPage(ButtonGroup pages) {
        return java.util.Collections.list(pages.getElements()).stream()
                .filter(javax.swing.AbstractButton::isSelected)
                .map(JToggleButton.class::cast).findFirst().orElseThrow();
    }
    private static String role(UserRole role) {
        return switch (role) { case STUDENT -> "学生"; case TEACHER -> "教师"; case ADMIN -> "管理员"; };
    }
    private static String status(AccountStatus status) {
        return switch (status) { case ACTIVE -> "正常"; case PENDING -> "待审核";
            case DISABLED -> "已停用"; case CANCELLED -> "已注销"; };
    }
    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run(); else SwingUtilities.invokeLater(action);
    }
}
