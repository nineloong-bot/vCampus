package edu.seu.vcampus.client.course.ui;

/**
 * Adjustment-window page for live add, drop, and atomic change commands.
 *
 * <p>Section formatting, mutations, the action bar and asynchronous loading are
 * implemented by the package-private segment chain this class extends, keeping the
 * public constructor and page contract unchanged.</p>
 */
public final class AdjustmentPanel extends AdjustmentPanelAssembly {

    /** Creates the adjustment page bound to the course gateway. */
    public AdjustmentPanel(CourseUiGateway gateway) {
        super(gateway);
    }

    AdjustmentPanel(CourseUiGateway gateway, ChangeConfirmation confirmation) {
        super(gateway, confirmation);
    }
}
