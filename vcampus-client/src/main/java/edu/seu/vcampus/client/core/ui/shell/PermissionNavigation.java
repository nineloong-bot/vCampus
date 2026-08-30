package edu.seu.vcampus.client.core.ui.shell;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JToggleButton;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Fixed demo navigation for the five top-level application areas. */
public final class PermissionNavigation extends JPanel {
    private static final Border ITEM_BORDER = BorderFactory.createEmptyBorder(
            0, UiSpacing.SPACE_6, 0, UiSpacing.SPACE_4);
    private static final Border ITEM_FOCUS_BORDER = new CompoundBorder(
            UiBorders.FOCUS,
            BorderFactory.createEmptyBorder(0, UiSpacing.SPACE_6 - 2,
                    0, UiSpacing.SPACE_4 - 2));

    /** Immutable navigation item used by the shared shell. */
    public record Item(String id, String title) { }

    /** Canonical top-level order mandated by the design system. */
    public static final List<Item> ITEMS = List.of(
            new Item("student", "学籍档案"),
            new Item("course", "课程中心"),
            new Item("library", "图书借阅"),
            new Item("shop", "校园商城"),
            new Item("account", "账户设置"));

    /** Creates the navigation and reports selected page identifiers. */
    public PermissionNavigation(Consumer<String> onSelected) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        Objects.requireNonNull(onSelected, "onSelected");
        setBackground(UiColors.BACKGROUND_NAV);
        setBorder(BorderFactory.createEmptyBorder());
        setPreferredSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, 0));
        setMinimumSize(new Dimension(UiDimensions.NAVIGATION_WIDTH, 0));
        ButtonGroup group = new ButtonGroup();
        for (int index = 0; index < ITEMS.size(); index++) {
            Item item = ITEMS.get(index);
            JToggleButton button = new JToggleButton(item.title());
            button.setUI(new BasicToggleButtonUI());
            button.setName("navigation." + item.id());
            button.getAccessibleContext().setAccessibleName(item.title() + "导航");
            button.setFocusPainted(false);
            button.setBorderPainted(true);
            button.setBorder(ITEM_BORDER);
            button.setMargin(new Insets(0, 0, 0, 0));
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setRolloverEnabled(true);
            button.setHorizontalAlignment(JToggleButton.LEFT);
            button.setAlignmentX(LEFT_ALIGNMENT);
            Dimension itemSize = new Dimension(UiDimensions.NAVIGATION_WIDTH,
                    UiDimensions.CONTROL_HEIGHT);
            button.setMinimumSize(itemSize);
            button.setPreferredSize(itemSize);
            button.setMaximumSize(itemSize);
            button.setSelected(index == 0);
            applySelectionStyle(button);
            installInteractionStyle(button);
            button.addActionListener(event -> {
                select(button);
                onSelected.accept(item.id());
            });
            group.add(button);
            add(button);
        }
    }

    private void select(JToggleButton selected) {
        selected.setSelected(true);
        for (java.awt.Component component : getComponents()) {
            if (!(component instanceof JToggleButton button)) continue;
            applySelectionStyle(button);
        }
    }

    private static void applySelectionStyle(JToggleButton button) {
        button.setBackground(button.isSelected() ? UiColors.PRIMARY : UiColors.BACKGROUND_NAV);
        button.setForeground(button.isSelected()
                ? UiColors.TEXT_ON_PRIMARY : UiColors.TEXT_PRIMARY);
    }

    private static void installInteractionStyle(JToggleButton button) {
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                button.setBackground(button.isSelected()
                        ? UiColors.PRIMARY_HOVER : UiColors.BACKGROUND_SUBTLE);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                applySelectionStyle(button);
            }
        });
        button.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                button.setBorder(ITEM_FOCUS_BORDER);
            }

            @Override
            public void focusLost(FocusEvent event) {
                button.setBorder(ITEM_BORDER);
            }
        });
    }
}
