package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.common.protocol.ResponseBody;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.FlowLayout;
import java.util.concurrent.CompletionStage;

/** Minimal connection/result status surface for the application shell. */
public final class ConnectionStatusPanel extends JPanel {
    private final JLabel status = new JLabel("未连接");

    /** Creates the status panel. */
    public ConnectionStatusPanel() {
        super(new FlowLayout(FlowLayout.RIGHT));
        add(status);
    }

    /** Observes an asynchronous response and updates the status only on the EDT. */
    public void observe(CompletionStage<? extends ResponseBody<?>> response) {
        response.whenComplete((body, error) -> onEdt(() -> {
            if (error != null) {
                setStatus("请求失败");
            } else if (body.success()) {
                setStatus("连接正常");
            } else {
                setStatus(body.message());
            }
        }));
    }

    /** Shows a transient busy state. */
    public void setBusy(boolean busy) {
        onEdt(() -> setStatus(busy ? "处理中…" : "就绪"));
    }

    /** Returns the currently displayed status text. */
    public String statusText() {
        return status.getText();
    }

    private void setStatus(String text) {
        String old = status.getText();
        status.setText(text);
        firePropertyChange("statusText", old, text);
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
