package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Window;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shows a short, non-sensitive warning before an invalidated shell is closed. */
final class SessionReplacementWarningDialog extends JDialog {
    private static final int DISPLAY_MILLIS = 5_000;
    private final Runnable onFinished;
    private final AtomicBoolean finished = new AtomicBoolean();
    private final Timer timer = new Timer(DISPLAY_MILLIS, event -> finish());

    private final SessionMonitor.InvalidationReason reason;

    SessionReplacementWarningDialog(Window owner,
            SessionMonitor.InvalidationReason reason, Runnable onFinished) {
        super(owner, reason == SessionMonitor.InvalidationReason.PASSWORD_RESET
                ? "密码安全提醒" : "登录安全提醒", Dialog.ModalityType.MODELESS);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.onFinished = Objects.requireNonNull(onFinished, "onFinished");
        timer.setRepeats(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(content());
        setSize(620, 210);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    void showWarning() {
        setVisible(true);
        timer.start();
    }

    private JPanel content() {
        JPanel panel = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.setBorder(UiBorders.pageInset());
        boolean passwordReset = reason == SessionMonitor.InvalidationReason.PASSWORD_RESET;
        JLabel title = new JLabel(passwordReset
                ? "密码已被管理员初始化" : "登录状态已失效");
        title.setFont(UiTypography.PAGE_TITLE);
        JLabel message = new JLabel(passwordReset
                ? "管理员已初始化你的登录密码，当前登录已失效。"
                        + "请使用初始密码重新登录，并按提示修改密码。"
                : "该账号已在其他位置登录，可能存在密码泄露。请及时修改密码。");
        message.setForeground(UiColors.ERROR_FG);
        message.getAccessibleContext().setAccessibleName(passwordReset
                ? "密码初始化安全提醒" : "异地登录安全警告");
        JLabel closing = new JLabel("此窗口将在约 5 秒后关闭，并返回登录页。");
        closing.setForeground(UiColors.TEXT_SECONDARY);
        JPanel body = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        body.setOpaque(false);
        body.add(message, BorderLayout.NORTH);
        body.add(closing, BorderLayout.CENTER);
        panel.add(title, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private void finish() {
        if (!finished.compareAndSet(false, true)) return;
        timer.stop();
        super.dispose();
        onFinished.run();
    }

    @Override
    public void dispose() {
        timer.stop();
        super.dispose();
    }
}
