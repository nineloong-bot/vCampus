package edu.seu.vcampus.client.core.ui.shell;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.ConnectionStatusPanel;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/** Shared top bar presenting product identity, signed-in identity, and connection state. */
public final class IdentityHeader extends JPanel {
    /** Creates the shared identity header without exposing session credentials. */
    public IdentityHeader(UserView user, ClientConnection connection) {
        super(new BorderLayout(UiSpacing.SPACE_4, 0));
        setBackground(UiColors.PRIMARY);
        setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.SPACE_6,
                0, UiSpacing.SPACE_6));
        setPreferredSize(new Dimension(0, UiDimensions.HEADER_HEIGHT));
        JLabel brand = label("vCampus · 虚拟校园", UiTypography.SECTION_TITLE);
        brand.setName("header.brand");
        brand.getAccessibleContext().setAccessibleName("vCampus 虚拟校园");
        add(brand, BorderLayout.WEST);

        JPanel trailing = new JPanel();
        trailing.setName("header.trailing");
        trailing.setOpaque(false);
        trailing.setLayout(new BoxLayout(trailing, BoxLayout.X_AXIS));
        if (user != null) {
            JLabel identity = label(user.loginId() + " · " + roleName(user.role()),
                    UiTypography.BODY);
            identity.setName("identity.summary");
            identity.getAccessibleContext().setAccessibleName("当前用户和角色");
            trailing.add(identity);
            trailing.add(Box.createHorizontalStrut(UiSpacing.SPACE_6));
        }
        ConnectionStatusPanel status = connection == null
                ? new ConnectionStatusPanel(true) : new ConnectionStatusPanel(connection, true);
        status.setOpaque(false);
        trailing.add(status);
        add(trailing, BorderLayout.EAST);
    }

    private static JLabel label(String text, java.awt.Font font) {
        JLabel label = new JLabel(text);
        label.setForeground(UiColors.TEXT_ON_PRIMARY);
        label.setFont(font);
        return label;
    }

    private static String roleName(UserRole role) {
        return switch (role) {
            case ADMIN -> "管理员";
            case TEACHER -> "教师";
            case STUDENT -> "学生";
        };
    }
}
