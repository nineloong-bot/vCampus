package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.student.service.StudentClientService;

import java.awt.BorderLayout;

import javax.swing.JPanel;

/**
 * Displays and maintains the academic organization hierarchy.
 *
 * <p>The tree loading, forms, save flows and toolbar actions are implemented
 * by the package-private segment chain this class extends, keeping the public
 * surface unchanged.</p>
 */
public final class OrganizationManagementPanel extends OrganizationManagementPanelView {

    /**
     * Creates the full organization-management workspace.
     *
     * @param students service used for organization data exchanges
     * @param connection client connection used for state listening
     */
    public OrganizationManagementPanel(StudentClientService students, ClientConnection connection) {
        this(students, connection, true, true);
    }

    /**
     * Creates an organization workspace with optional department-level maintenance.
     *
     * @param students service used for organization data exchanges
     * @param connection client connection used for state listening
     * @param departmentManagementAllowed whether department editing is permitted
     */
    public OrganizationManagementPanel(StudentClientService students, ClientConnection connection,
            boolean departmentManagementAllowed) {
        this(students, connection, departmentManagementAllowed, true);
    }

    /**
     * Creates an organization workspace with optional department and class-level maintenance.
     *
     * @param students service used for organization data exchanges
     * @param connection client connection used for state listening
     * @param departmentManagementAllowed whether department editing is permitted
     * @param classManagementAllowed whether class editing is permitted
     */
    public OrganizationManagementPanel(StudentClientService students, ClientConnection connection,
            boolean departmentManagementAllowed, boolean classManagementAllowed) {
        super(students, connection, departmentManagementAllowed, classManagementAllowed);
        setLayout(new BorderLayout(UiSpacing.SPACE_4, 0));
        setName("student.org");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        buildPage();
        connection.addStateListener(this::connectionChanged);
    }

    @Override public void addNotify() {
        super.addNotify();
        active = true;
        connectionChanged(connection.state());
        loadAll();
    }

    @Override public void removeNotify() {
        active = false;
        requestGeneration.incrementAndGet();
        super.removeNotify();
    }
}
