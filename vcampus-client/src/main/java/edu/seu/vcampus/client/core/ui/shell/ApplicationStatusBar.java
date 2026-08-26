package edu.seu.vcampus.client.core.ui.shell;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Compact application status and local-time footer. */
public final class ApplicationStatusBar extends JPanel {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm");

    public ApplicationStatusBar() {
        super(new BorderLayout());
        setBackground(UiColors.BACKGROUND_SUBTLE);
        setBorder(BorderFactory.createCompoundBorder(
                UiBorders.STATUS_TOP,
                BorderFactory.createEmptyBorder(0, UiSpacing.MD, 0, UiSpacing.MD)));
        setPreferredSize(new Dimension(0, UiDimensions.STATUS_BAR_HEIGHT));
        add(caption("就绪"), BorderLayout.WEST);
        add(caption(LocalDateTime.now().format(TIME_FORMAT)), BorderLayout.EAST);
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        return label;
    }
}
