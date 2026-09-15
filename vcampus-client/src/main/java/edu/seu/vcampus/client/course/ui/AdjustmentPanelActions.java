package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

/** Action bar wiring for the adjustment panel segments. */
abstract class AdjustmentPanelActions extends AdjustmentPanelMutations {

    AdjustmentPanelActions(CourseUiGateway gateway) {
        super(gateway);
    }

    AdjustmentPanelActions(CourseUiGateway gateway, ChangeConfirmation confirmation) {
        super(gateway, confirmation);
    }

    JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT));
        panel.add(label("改选将同时锁定原教学班和目标教学班", UiTypography.CAPTION, UiColors.TEXT_SECONDARY));
        panel.add(Box.createHorizontalGlue());
        add.setEnabled(false);
        add.addActionListener(event -> lateAdd(add));
        panel.add(add);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        drop.setEnabled(false);
        drop.addActionListener(event -> drop(drop));
        panel.add(drop);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        change.setEnabled(false);
        change.addActionListener(event -> change(change));
        panel.add(change);
        return panel;
    }
}
