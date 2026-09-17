package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiSpacing;

import javax.swing.*;
import java.awt.*;

/** Keeps batch controls and option-finalization controls on separate visible rows. */
final class MajorTransferCollegeToolbar extends JPanel {
    MajorTransferCollegeToolbar(JComboBox<?> batches, JButton maintainOption, JButton refresh,
            JComboBox<?> finalizationOptions, JButton finalizeOption, JButton effectiveOption,
            JButton rollbackOption, JLabel readiness) {
        super(new GridLayout(2, 1, 0, UiSpacing.SPACE_1));
        setOpaque(false);
        add(row(new JLabel("批次："), batches, maintainOption, refresh));
        add(row(new JLabel("终审专业："), finalizationOptions, finalizeOption,
                effectiveOption, rollbackOption, readiness));
    }

    private static JPanel row(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.setOpaque(false);
        for (Component component : components) row.add(component);
        return row;
    }
}
