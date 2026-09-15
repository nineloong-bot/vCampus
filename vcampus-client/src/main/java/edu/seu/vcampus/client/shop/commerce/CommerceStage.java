package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import javax.swing.*;
import java.awt.*;

/** Hosts commerce editors inside the current page instead of an overlay window. */
final class CommerceStage extends JPanel {
    private final EmbeddedEditorHost host;
    private final Runnable close;
    private final JLabel message = CommerceTheme.muted(" ");

    CommerceStage(JComponent base, Runnable close) {
        super(new BorderLayout()); this.close = close;
        host = new EmbeddedEditorHost(base); add(host);
    }

    void showDialog(String title, JComponent main, JComponent footer, int requestedWidth) {
        host.showEditor(new CommerceEditorBridge(title, main, footer, message,
                requestedWidth, close));
        revalidate(); repaint();
    }

    boolean isModal() { return host.isEditorOpen(); }
    boolean requestClose() { return host.requestClose(); }
    void hideDialog() { host.completeAndClose(); }
    void notice(String value) { message.setText(value); }
}
