package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/** Compatibility wrapper around the role-specific transfer workspaces. */
public final class MajorTransferAdminPanel extends JPanel {
    /** Selects the central batch workspace or the college processing workspace. */
    public enum TransferAdminMode { CENTRAL_MANAGEMENT, COLLEGE_APPROVAL }

    /** Creates the central batch-management workspace. */
    public MajorTransferAdminPanel(StudentClientService students,
            ClientConnection connection) {
        this(students, connection, TransferAdminMode.CENTRAL_MANAGEMENT);
    }

    /** Creates the transfer workspace selected by the authenticated role. */
    public MajorTransferAdminPanel(StudentClientService students,
            ClientConnection connection, TransferAdminMode mode) {
        super(new BorderLayout());
        Objects.requireNonNull(connection);
        Objects.requireNonNull(mode);
        setName("major-transfer.admin");
        add(mode == TransferAdminMode.CENTRAL_MANAGEMENT
                ? new MajorTransferBatchManagementPanel(students)
                : new MajorTransferCollegeProcessingPanel(students));
    }
}
