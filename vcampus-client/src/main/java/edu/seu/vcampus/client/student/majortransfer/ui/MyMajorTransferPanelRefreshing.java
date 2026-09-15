package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.SwingUtilities;

/** Workspace loading flow for the major-transfer panel. */
abstract class MyMajorTransferPanelRefreshing extends MyMajorTransferPanelRendering {

    /** Creates the refreshing segment of the major-transfer panel. */
    protected MyMajorTransferPanelRefreshing(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    void refresh() {
        if (!active) return;
        long gen = generation.incrementAndGet();
        statusLabel.setText("正在加载...");
        errorLabel.setText(" ");
        students.getTransferWorkspace().whenComplete((response, error) -> {
            if (generation.get() != gen || !active) return;
            SwingUtilities.invokeLater(() -> {
                if (generation.get() != gen || !active) return;
                if (response != null && response.success()) {
                    render(response.data());
                } else {
                    errorLabel.setText(response != null ? response.message() : "网络错误");
                    statusLabel.setText("加载失败");
                }
            });
        });
    }
}
