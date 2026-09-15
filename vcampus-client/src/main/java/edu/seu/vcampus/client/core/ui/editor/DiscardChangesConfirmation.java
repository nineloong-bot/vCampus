package edu.seu.vcampus.client.core.ui.editor;

import javax.swing.JOptionPane;
import java.awt.Component;

/** Supplies the short confirmation used before discarding dirty editor state. */
@FunctionalInterface
public interface DiscardChangesConfirmation {
    /** Returns whether the dirty editor may be closed. */
    boolean confirm(Component owner);

    /** Returns the standard production confirmation. */
    static DiscardChangesConfirmation standard() {
        return owner -> JOptionPane.showConfirmDialog(owner,
                "当前修改尚未保存，确定放弃吗？", "放弃修改",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
