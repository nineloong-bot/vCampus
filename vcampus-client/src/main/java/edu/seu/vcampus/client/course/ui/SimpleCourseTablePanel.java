package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;

/** Spec-shaped list/management scaffold shared only as page composition, not a private theme. */
abstract class SimpleCourseTablePanel extends AbstractCoursePanel {
    protected SimpleCourseTablePanel(String title, String description, String action, Object[] columns, Object[][] rows) {
        super(title, description);
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(UiColors.BACKGROUND_SUBTLE);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG)));
        toolbar.add(label("按当前学期显示", UiTypography.BODY, UiColors.TEXT_PRIMARY), BorderLayout.WEST);
        toolbar.add(primary(action), BorderLayout.EAST);
        body.add(toolbar, BorderLayout.NORTH);
        JPanel listing = new JPanel(new BorderLayout(0, UiSpacing.MD));
        listing.setOpaque(false);
        listing.add(new JLabel("共 " + rows.length + " 条"), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table(rows, columns));
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        listing.add(scroll, BorderLayout.CENTER);
        JPanel pager = new JPanel(); pager.setOpaque(false);
        pager.add(secondary("上一页")); pager.add(new JLabel("第 1 / 1 页")); pager.add(secondary("下一页"));
        listing.add(pager, BorderLayout.SOUTH);
        body.add(listing, BorderLayout.CENTER);
    }
}
