package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.common.course.OfferingSummary;

import java.awt.Window;

/**
 * Modal create/edit form for an offering aggregate and all of its schedule rows.
 *
 * <p>Reference loading, form layout, submission and dialog wiring are implemented by
 * the package-private segment chain this class extends, keeping the dialog type and
 * constructor usable from the course UI package.</p>
 */
final class OfferingEditorDialog extends OfferingEditorDialogAssembly {

    OfferingEditorDialog(Window owner, CourseUiGateway gateway, OfferingSummary existing, Runnable onSaved) {
        super(owner, gateway, existing, onSaved);
    }
}
