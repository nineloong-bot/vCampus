package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.student.service.StudentClientService;

import java.awt.BorderLayout;

/**
 * Student major-transfer workspace panel.
 *
 * <p>The page layout, application form, rendering and draft/submit/withdraw
 * flows are implemented by the package-private segment chain this class
 * extends, keeping the public surface unchanged.</p>
 */
public final class MyMajorTransferPanel extends MyMajorTransferPanelLayout {

    /**
     * Creates the full major-transfer workspace.
     *
     * @param students service used for transfer data exchanges
     * @param connection client connection used for state listening
     */
    public MyMajorTransferPanel(StudentClientService students, ClientConnection connection) {
        super(students, connection);
        setLayout(new BorderLayout(0, UiSpacing.SPACE_4));
        setName("major-transfer.student");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        build();
        connection.addStateListener(this::connectionChanged);
    }

    @Override public void addNotify() { super.addNotify(); active = true; refresh(); }
    @Override public void removeNotify() { active = false; generation.incrementAndGet(); super.removeNotify(); }
}
