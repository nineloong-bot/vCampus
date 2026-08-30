package edu.seu.vcampus.client.core.ui.shell;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;

/** Reusable structured placeholder for modules not implemented in this UI slice. */
public final class ModulePlaceholderPage extends JPanel {
    /** Creates an accessible module placeholder with purpose and construction status. */
    public ModulePlaceholderPage(String title, String description) {
        super(new BorderLayout());
        setName("page." + pageId(title));
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        setFocusable(true);
        getAccessibleContext().setAccessibleName(title + "功能建设中页面");

        JPanel heading = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_2));
        heading.setOpaque(false);
        JLabel breadcrumb = new JLabel("虚拟校园 / " + title);
        breadcrumb.setName("page.breadcrumb");
        breadcrumb.setFont(UiTypography.CAPTION);
        breadcrumb.setForeground(UiColors.TEXT_SECONDARY);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setName("page.title");
        titleLabel.setFont(UiTypography.PAGE_TITLE);
        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setName("page.description");
        descriptionLabel.setFont(UiTypography.BODY);
        descriptionLabel.setForeground(UiColors.TEXT_SECONDARY);
        heading.add(breadcrumb);
        heading.add(titleLabel);
        heading.add(descriptionLabel);
        add(heading, BorderLayout.NORTH);

        JLabel status = new JLabel("功能建设中", JLabel.CENTER);
        status.setName("page.status");
        status.getAccessibleContext().setAccessibleName(title + "功能建设中状态");
        status.setFont(UiTypography.SECTION_TITLE);
        status.setForeground(UiColors.TEXT_SECONDARY);
        status.setBorder(UiBorders.LINE);
        add(status, BorderLayout.CENTER);
    }

    private static String pageId(String title) {
        return switch (title) {
            case "学籍档案" -> "student";
            case "课程中心" -> "course";
            case "图书借阅" -> "library";
            case "校园商城" -> "shop";
            default -> "account";
        };
    }
}
