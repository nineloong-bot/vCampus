package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;

/** Wires the adjustment page body and triggers the first load. */
abstract class AdjustmentPanelAssembly extends AdjustmentPanelRefreshing {

    AdjustmentPanelAssembly(CourseUiGateway gateway) {
        super(gateway);
    }

    AdjustmentPanelAssembly(CourseUiGateway gateway, ChangeConfirmation confirmation) {
        super(gateway, confirmation);
    }

    @Override
    void initializePanel() {
        JPanel phaseBar = new JPanel(new BorderLayout(UiSpacing.MD, 0));
        phaseBar.setBackground(UiColors.BACKGROUND_SUBTLE);
        phaseBar.add(phaseSummary, BorderLayout.CENTER);
        JButton refresh = secondary("刷新调整数据");
        refresh.addActionListener(event -> refresh());
        phaseBar.add(refresh, BorderLayout.EAST);
        body.add(phaseBar, BorderLayout.NORTH);

        JPanel tables = new JPanel(new GridLayout(2, 1, 0, UiSpacing.LG));
        tables.setOpaque(false);
        tables.add(section("当前选课：选择需要退选或改选的记录", enrollmentTable));
        tables.add(section("目标教学班：选择补选或改选目标", offeringTable));
        body.add(tables, BorderLayout.CENTER);
        body.add(actions(), BorderLayout.SOUTH);
        refresh();
    }
}
