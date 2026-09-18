package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.JButton;
import java.util.Objects;

/** Consistent manual-refresh control for student administration workspaces. */
final class ManualRefreshButton extends JButton {
    ManualRefreshButton(String componentName, Runnable refreshAction) {
        super("刷新");
        setName(Objects.requireNonNull(componentName, "componentName"));
        setFont(UiTypography.BODY);
        getAccessibleContext().setAccessibleName("刷新当前状态");
        addActionListener(event -> refreshAction.run());
    }
}
