package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.student.service.StudentClientService;

import java.awt.BorderLayout;

/**
 * Student search workspace with filter bar, paged results and detail pane.
 *
 * <p>The filter widgets, hierarchy loading, query execution and detail binding
 * are implemented by the package-private segment chain this class extends,
 * keeping the public surface unchanged.</p>
 */
public final class StudentSearchPanel extends StudentSearchPanelView {

    /**
     * Creates the full student search workspace.
     *
     * @param students service used for student data exchanges
     * @param connection client connection used for state listening
     * @param canEdit whether the detail pane allows editing the selected student
     */
    public StudentSearchPanel(StudentClientService students, ClientConnection connection,
                              boolean canEdit) {
        super(students, connection, canEdit);
        setLayout(new BorderLayout(0, UiSpacing.SPACE_3));
        setName("student.search");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        buildPage();
        connection.addStateListener(this::connectionChanged);
    }

    @Override public void addNotify() {
        super.addNotify();
        active = true;
        connectionChanged(connection.state());
        long generation = requestGeneration.incrementAndGet();
        loadDepartments(generation);
        executeSearch(generation);
    }

    @Override public void removeNotify() {
        active = false;
        requestGeneration.incrementAndGet();
        super.removeNotify();
    }
}
