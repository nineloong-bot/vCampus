package edu.seu.vcampus.client.core.ui.shell;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Fixed-order navigation for the five reviewed business modules. */
public final class PermissionNavigation extends JPanel {
    private final Map<String, JButton> buttons = new LinkedHashMap<>();
    private final Consumer<String> selectionListener;

    public PermissionNavigation(Consumer<String> selectionListener) {
        this.selectionListener = selectionListener;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UiColors.BACKGROUND_NAV);
        setBorder(UiBorders.NAVIGATION_RIGHT);
        setPreferredSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, 0));
        setMinimumSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, 0));
        setMaximumSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, Integer.MAX_VALUE));
        add(Box.createVerticalStrut(UiSpacing.LG));
    }

    /** Adds one permitted navigation entry. */
    public void addEntry(String pageId, String label) {
        JButton button = new JButton(label);
        button.setFont(UiTypography.BODY_BOLD);
        button.setForeground(UiColors.TEXT_PRIMARY);
        button.setBackground(UiColors.BACKGROUND_NAV);
        button.setHorizontalAlignment(JButton.LEFT);
        button.setMargin(new Insets(0, UiSpacing.XL, 0, UiSpacing.MD));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setAlignmentX(LEFT_ALIGNMENT);
        Dimension size = new Dimension(UiDimensions.NAVIGATION_WIDTH - 1,
                UiDimensions.NAVIGATION_ITEM_HEIGHT);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.getAccessibleContext().setAccessibleName(label);
        button.addActionListener(event -> select(pageId));
        buttons.put(pageId, button);
        add(button);
    }

    /** Selects an entry and displays its reviewed visual state. */
    public void select(String pageId) {
        if (!buttons.containsKey(pageId)) {
            throw new IllegalArgumentException("Unknown navigation page: " + pageId);
        }
        buttons.forEach((id, button) -> {
            boolean selected = id.equals(pageId);
            button.setBackground(selected ? UiColors.PRIMARY : UiColors.BACKGROUND_NAV);
            button.setForeground(selected ? UiColors.TEXT_ON_PRIMARY : UiColors.TEXT_PRIMARY);
        });
        selectionListener.accept(pageId);
    }

    /** Returns visible labels in their fixed order. */
    public List<String> labels() {
        List<String> labels = new ArrayList<>();
        buttons.values().forEach(button -> labels.add(button.getText()));
        return List.copyOf(labels);
    }
}
