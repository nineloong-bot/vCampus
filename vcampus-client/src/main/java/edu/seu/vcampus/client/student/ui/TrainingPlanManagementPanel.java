package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;

/**
 * Admin panel for managing training plans and their courses.
 *
 * <p>The view and dialog flows are assembled by the package-private segment
 * chain this class extends, keeping the public surface unchanged.</p>
 */
public final class TrainingPlanManagementPanel extends TrainingPlanManagementPanelMainView {

    /**
     * Creates the training plan management panel backed by the given service.
     *
     * @param students service used for all training plan data exchanges
     */
    public TrainingPlanManagementPanel(StudentClientService students) {
        super(students);
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buildUi();
        buildWorkspacePanel();
        loadDepartments();
    }
}
