package edu.seu.vcampus.client.core.ui.shell;

import edu.seu.vcampus.client.core.ui.ConnectionStatusPanel;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/** Product, authenticated identity, and connection state header. */
public final class IdentityHeader extends JPanel {
    public IdentityHeader(UserView user) {
        super(new BorderLayout());
        setBackground(UiColors.PRIMARY);
        setBorder(BorderFactory.createCompoundBorder(
                UiBorders.HEADER_BOTTOM,
                BorderFactory.createEmptyBorder(0, UiSpacing.XL, 0, UiSpacing.XL)));
        setPreferredSize(new Dimension(0, UiDimensions.HEADER_HEIGHT));

        JLabel product = new JLabel("vCampus · 虚拟校园");
        product.setFont(UiTypography.DISPLAY);
        product.setForeground(UiColors.TEXT_ON_PRIMARY);
        add(product, BorderLayout.WEST);

        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.X_AXIS));
        JLabel userLabel = new JLabel(user == null
                ? "访客"
                : user.loginId() + "  ·  " + roleName(user));
        userLabel.setFont(UiTypography.BODY);
        userLabel.setForeground(UiColors.TEXT_ON_PRIMARY);
        identity.add(userLabel);
        identity.add(Box.createHorizontalStrut(UiSpacing.XL));
        identity.add(new ConnectionStatusPanel(user == null ? "未连接" : "连接正常"));
        add(identity, BorderLayout.EAST);
    }

    private static String roleName(UserView user) {
        return switch (user.role()) {
            case STUDENT -> "学生";
            case TEACHER -> "教师";
            case ADMIN -> "管理员";
        };
    }
}
