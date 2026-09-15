package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.common.course.OfferingSummary;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;

/** Wires the assembled offering editor: root layout, listeners and disposal. */
abstract class OfferingEditorDialogAssembly extends OfferingEditorDialogSubmitting {

    OfferingEditorDialogAssembly(Window owner, CourseUiGateway gateway, OfferingSummary existing,
            Runnable onSaved) {
        super(owner, gateway, existing, onSaved);
    }

    @Override
    void initializeDialog(Window owner) {
        JPanel root = new JPanel(new BorderLayout(0, UiSpacing.LG));
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.XL, UiSpacing.XL, UiSpacing.XL, UiSpacing.XL));
        root.add(title(), BorderLayout.NORTH);
        root.add(form(), BorderLayout.CENTER);
        save.setEnabled(false);
        save.addActionListener(event -> submit());
        retry.setEnabled(false);
        retry.addActionListener(event -> loadReferences());
        root.add(actions(), BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(save);
        if (existing == null) schedules.addDefaultRow();
        else fill(existing);
        setSize(new Dimension(840, 780));
        setLocationRelativeTo(owner);
        loadReferences();
    }

    @Override public void dispose() {
        active = false;
        referenceSequence++;
        asyncGuard.deactivate();
        super.dispose();
    }
}
