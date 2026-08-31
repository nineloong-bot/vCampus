package edu.seu.vcampus.client.core.ui.shell;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Shared bottom bar for concise application status. */
public final class ApplicationStatusBar extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    /** Creates a status bar initialized to the ready state. */
    public ApplicationStatusBar() {
        super(new BorderLayout());
        setBackground(UiColors.BACKGROUND_SUBTLE);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                UiColors.BORDER_DEFAULT));
        setPreferredSize(new Dimension(0, UiDimensions.STATUS_HEIGHT));
        JLabel status = new JLabel("就绪");
        status.setName("status.message");
        status.setFont(UiTypography.CAPTION);
        status.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.SPACE_4, 0, 0));
        add(status, BorderLayout.WEST);
        JLabel date = new JLabel(LocalDate.now().format(DATE_FORMAT));
        date.setName("status.date");
        date.setFont(UiTypography.CAPTION);
        date.setForeground(UiColors.TEXT_SECONDARY);
        date.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, UiSpacing.SPACE_4));
        add(date, BorderLayout.EAST);
    }
}
