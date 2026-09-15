package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.client.core.ui.editor.*;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;

/** Adapts commerce forms to the shared page-embedded editor contract. */
final class CommerceEditorBridge implements EmbeddedEditor {
    static final String CONFIRM_DISCARD = "commerce.confirmDiscard";
    private final JPanel root = CommerceTheme.card(Color.WHITE, 24);
    private final EditorSize size;
    private final boolean confirmDiscard;
    private boolean dirty;

    CommerceEditorBridge(String title, JComponent main, JComponent footer,
            JLabel message, int requestedWidth, Runnable close) {
        size = requestedWidth > 720 ? EditorSize.WIDE : EditorSize.COMPACT;
        confirmDiscard = !Boolean.FALSE.equals(main.getClientProperty(CONFIRM_DISCARD));
        root.setName("commerce.editor");
        JPanel header = new JPanel(new BorderLayout(12, 0)); header.setOpaque(false);
        header.add(CommerceTheme.heading(title, 22));
        JButton cancel = CommerceTheme.button("关闭", close);
        cancel.setName("commerce.editor.close"); header.add(cancel, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        root.add(header, BorderLayout.NORTH);
        root.add(main instanceof JScrollPane || main.getLayout() instanceof BorderLayout
                ? main : CommerceTheme.scroll(main), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout()); bottom.setOpaque(false);
        if (footer != null) {
            footer.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, CommerceTheme.LINE),
                    BorderFactory.createEmptyBorder(12, 0, 0, 0)));
            bottom.add(footer);
        }
        message.setText(" "); bottom.add(message, BorderLayout.SOUTH); root.add(bottom, BorderLayout.SOUTH);
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return size; }
    @Override public void onOpened() {
        if (confirmDiscard) install(root);
        dirty = false;
    }
    @Override public boolean isDirty() { return dirty; }

    private void install(Component component) {
        if (component instanceof JTextComponent text && text.isEditable()) {
            text.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { dirty = true; }
                @Override public void removeUpdate(DocumentEvent event) { dirty = true; }
                @Override public void changedUpdate(DocumentEvent event) { dirty = true; }
            });
        } else if (component instanceof JComboBox<?> combo) combo.addActionListener(event -> dirty = true);
        else if (component instanceof JCheckBox check) check.addActionListener(event -> dirty = true);
        else if (component instanceof JRadioButton radio) radio.addActionListener(event -> dirty = true);
        else if (component instanceof JToggleButton toggle) toggle.addActionListener(event -> dirty = true);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) install(child);
            container.addContainerListener(new ContainerAdapter() {
                @Override public void componentAdded(ContainerEvent event) { install(event.getChild()); }
            });
        }
    }
}
