package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.student.service.StudentClientService;

import java.awt.BorderLayout;

/**
 * Student profile workspace with draft, approval and formal-PDF workflow.
 *
 * <p>The field grids, edit state, submission flows and rendering are
 * implemented by the package-private segment chain this class extends,
 * keeping the public surface unchanged.</p>
 */
public final class MyStudentProfilePanel extends MyStudentProfilePanelLayout {

    /**
     * Creates the full profile workspace.
     *
     * @param students service used for profile data exchanges
     * @param connection client connection used for state listening
     */
    public MyStudentProfilePanel(StudentClientService students, ClientConnection connection) {
        super(students, connection);
        setLayout(new BorderLayout(0, UiSpacing.SPACE_4));
        setName("student.profile"); setBackground(UiColors.BACKGROUND_PAGE); setBorder(UiBorders.pageInset());
        build(); connection.addStateListener(this::connectionChanged);
    }

    @Override public void addNotify() { super.addNotify(); active = true; refreshProfile(); }
    @Override public void removeNotify() { active = false; generation.incrementAndGet(); super.removeNotify(); }
}
