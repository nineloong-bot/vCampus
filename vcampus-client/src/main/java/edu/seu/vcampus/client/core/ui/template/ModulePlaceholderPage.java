package edu.seu.vcampus.client.core.ui.template;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Honest temporary module page used until business views are implemented. */
public final class ModulePlaceholderPage extends JPanel {
    public ModulePlaceholderPage(String title, String description, String scope) {
        super(new BorderLayout());
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(BorderFactory.createEmptyBorder(
                UiSpacing.PAGE_PADDING, UiSpacing.PAGE_PADDING,
                UiSpacing.PAGE_PADDING, UiSpacing.PAGE_PADDING));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel breadcrumb = label("虚拟校园  /  " + title, UiTypography.CAPTION, UiColors.TEXT_SECONDARY);
        JLabel pageTitle = label(title, UiTypography.PAGE_TITLE, UiColors.TEXT_PRIMARY);
        heading.add(breadcrumb);
        heading.add(Box.createVerticalStrut(UiSpacing.SM));
        heading.add(pageTitle);
        heading.add(Box.createVerticalStrut(UiSpacing.SM));
        heading.add(label(description, UiTypography.BODY, UiColors.TEXT_SECONDARY));
        add(heading, BorderLayout.NORTH);

        JPanel state = new JPanel();
        state.setOpaque(false);
        state.setLayout(new BoxLayout(state, BoxLayout.Y_AXIS));
        state.setBorder(BorderFactory.createCompoundBorder(
                UiBorders.SECTION,
                BorderFactory.createEmptyBorder(UiSpacing.XL, 0, UiSpacing.XL, 0)));
        JLabel stateTitle = label("功能建设中", UiTypography.SECTION_TITLE, UiColors.ACCENT);
        JLabel scopeLabel = label("计划范围：" + scope, UiTypography.BODY, UiColors.TEXT_SECONDARY);
        stateTitle.setAlignmentX(LEFT_ALIGNMENT);
        scopeLabel.setAlignmentX(LEFT_ALIGNMENT);
        state.add(stateTitle);
        state.add(Box.createVerticalStrut(UiSpacing.SM));
        state.add(scopeLabel);
        state.add(Box.createVerticalGlue());
        add(state, BorderLayout.CENTER);
    }

    private static JLabel label(String text, java.awt.Font font, java.awt.Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }
}
