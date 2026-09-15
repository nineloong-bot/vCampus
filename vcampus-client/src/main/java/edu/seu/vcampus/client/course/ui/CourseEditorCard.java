package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Shared bordered surface for embedded course administration editors. */
public final class CourseEditorCard {
    private CourseEditorCard() { }

    /** Wraps editor content and actions in a clearly bounded themed card. */
    public static JPanel create(JComponent content, JComponent actions) {
        return create(content, actions, BorderLayout.CENTER);
    }

    /** Wraps a short editor form at the top so controls keep their natural height. */
    public static JPanel createCompact(JComponent content, JComponent actions) {
        return create(content, actions, BorderLayout.NORTH);
    }

    private static JPanel create(JComponent content, JComponent actions, String position) {
        JPanel card = new JPanel(new BorderLayout(0, UiSpacing.MD));
        card.setName("course-editor-card");
        card.setBackground(new Color(248, 245, 236));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG)));
        card.add(content, position);
        if (actions != null) {
            actions.setName("course-editor-actions");
            card.add(actions, BorderLayout.SOUTH);
        }
        return card;
    }
}
