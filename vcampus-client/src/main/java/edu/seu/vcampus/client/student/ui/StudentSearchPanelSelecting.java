package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.SwingUtilities;

/** Selection-to-detail binding for the student search panel. */
abstract class StudentSearchPanelSelecting extends StudentSearchPanelBase {

    /** Creates the selection segment of the student search panel. */
    protected StudentSearchPanelSelecting(StudentClientService students, ClientConnection connection,
            boolean canEdit) {
        super(students, connection, canEdit);
    }

    void onStudentSelected() {
        int row = resultsTable.getSelectedRow();
        if (row < 0 || row >= currentResults.size()) {
            showPlaceholder();
            return;
        }
        String studentId = currentResults.get(row).studentId();
        showDetail(studentId);
    }

    void showDetail(String studentId) {
        if (detailPanel == null) {
            // Save divider location before replacing component to prevent layout jump
            int savedLocation = splitPane.getDividerLocation();
            detailPanel = new StudentDetailPanel(students, connection, studentId, canEdit);
            splitPane.setRightComponent(detailPanel);
            // Restore divider location after component swap
            SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(savedLocation));
        } else {
            detailPanel.loadStudent(studentId);
        }
    }

    void showPlaceholder() {
        if (detailPanel != null) {
            detailPanel.clear();
        }
    }
}
