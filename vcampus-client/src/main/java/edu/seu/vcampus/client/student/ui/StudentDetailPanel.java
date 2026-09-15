package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;

/**
 * Administrator-facing complete student profile with academic-only editing.
 *
 * <p>Field grids, change history, profile loading and rendering are implemented by
 * the package-private segment chain this class extends, keeping the public type and
 * constructor unchanged.</p>
 */
public final class StudentDetailPanel extends StudentDetailPanelLoading {

    /** Creates the detail panel bound to one student id and an optional edit grant. */
    public StudentDetailPanel(StudentClientService students, ClientConnection connection,
                              String studentId, boolean canEdit) {
        super(students, connection, studentId, canEdit);
    }
}
