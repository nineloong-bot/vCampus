package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.protocol.ResponseBody;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.FlowLayout;
import java.util.concurrent.CompletionStage;
import java.util.Objects;

/** Minimal connection/result status surface for the application shell. */
public final class ConnectionStatusPanel extends JPanel {
    private final JLabel status = new JLabel("未连接");
    private final boolean onPrimary;

    /** Creates the status panel. */
    public ConnectionStatusPanel() {
        this(false);
    }

    /** Creates an unbound status panel for a light or primary-color surface. */
    public ConnectionStatusPanel(boolean onPrimary) {
        super(new FlowLayout(FlowLayout.RIGHT));
        this.onPrimary = onPrimary;
        status.setName("connection.status");
        status.getAccessibleContext().setAccessibleName("服务器连接状态");
        status.setFont(UiTypography.CAPTION);
        status.setForeground(onPrimary ? UiColors.TEXT_ON_PRIMARY : UiColors.TEXT_SECONDARY);
        add(status);
    }

    /** Creates a status panel bound to the real shared connection lifecycle. */
    public ConnectionStatusPanel(ClientConnection connection) {
        this(connection, false);
    }

    /** Creates a live status panel suitable for a dark primary-color surface. */
    public ConnectionStatusPanel(ClientConnection connection, boolean onPrimary) {
        this(onPrimary);
        ClientConnection observed = Objects.requireNonNull(connection, "connection");
        showConnectionState(observed.state());
        observed.addStateListener(this::showConnectionState);
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

    private void showConnectionState(ConnectionState state) {
        onEdt(() -> {
            if (onPrimary) status.setForeground(UiColors.TEXT_ON_PRIMARY);
            switch (state) {
                case CONNECTED -> {
                    if (!onPrimary) status.setForeground(UiColors.SUCCESS_FG);
                    setStatus("服务器已连接");
                }
                case CONNECTING -> {
                    if (!onPrimary) status.setForeground(UiColors.TEXT_SECONDARY);
                    setStatus("正在连接服务器…");
                }
                case FAILED -> {
                    if (!onPrimary) status.setForeground(UiColors.ERROR_FG);
                    setStatus("服务器连接失败");
                }
                case DISCONNECTED -> {
                    if (!onPrimary) status.setForeground(UiColors.ERROR_FG);
                    setStatus("服务器未连接");
                }
            }
        });
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
