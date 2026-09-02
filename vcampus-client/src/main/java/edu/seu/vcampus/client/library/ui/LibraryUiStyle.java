package edu.seu.vcampus.client.library.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/** Applies a restrained modern treatment only inside the library workspace. */
final class LibraryUiStyle {
    private static final Set<String> PRIMARY_ACTIONS = Set.of(
            "查询馆藏", "新增书目", "新增副本", "保存策略", "借阅所选副本", "确认借阅");

    private LibraryUiStyle() { }

    static void apply(Component component) {
        if (component instanceof JButton button) styleButton(button);
        else if (component instanceof JTextField field) styleInput(field);
        else if (component instanceof JComboBox<?> combo) styleInput(combo);
        else if (component instanceof JSpinner spinner) styleInput(spinner);
        else if (component instanceof JLabel label && label.getFont() != null
                && label.getFont().getSize() <= 14)
            label.setFont(label.getFont().getSize() <= 12 ? LibraryPalette.CAPTION : LibraryPalette.BODY);
        if (component instanceof Container container)
            for (Component child : container.getComponents()) apply(child);
    }

    static void styleTabs(JTabbedPane tabs) {
        tabs.setFont(LibraryPalette.BODY.deriveFont(Font.BOLD));
        tabs.setBackground(LibraryPalette.PAGE);
        tabs.setForeground(LibraryPalette.TEXT);
        tabs.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    }

    private static void styleButton(JButton button) {
        boolean primary = PRIMARY_ACTIONS.contains(button.getText());
        button.setFont(LibraryPalette.BODY.deriveFont(Font.BOLD));
        button.setForeground(primary ? Color.WHITE : LibraryPalette.PRIMARY);
        button.setBackground(primary ? LibraryPalette.PRIMARY : LibraryPalette.SURFACE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? LibraryPalette.PRIMARY : LibraryPalette.BORDER),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)));
    }

    private static void styleInput(JComponent input) {
        input.setFont(LibraryPalette.BODY);
        input.setBackground(LibraryPalette.SURFACE);
        input.setForeground(LibraryPalette.TEXT);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LibraryPalette.BORDER),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    }
}
