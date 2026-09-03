package edu.seu.vcampus.client.shop.ui.style;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.LayoutManager;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

/** Shared theme-backed Shop UI kit implementation. */
public final class SharedShopUiKitAdapter implements ShopUiKit {
    @Override
    public JButton navigationButton(String name, String text) {
        return styleButton(named(new JButton(text), name), Style.NAVIGATION, false);
    }

    @Override
    public JButton primaryButton(String name, String text) {
        return styleButton(named(new JButton(text), name), Style.PRIMARY, true);
    }

    @Override
    public JButton secondaryButton(String name, String text) {
        return styleButton(named(new JButton(text), name), Style.SECONDARY, false);
    }

    @Override
    public JPanel filterPanel(String name, LayoutManager layout) {
        JPanel panel = named(new JPanel(layout == null ? new BorderLayout() : layout), name);
        panel.setBackground(UiColors.BACKGROUND_SUBTLE);
        return panel;
    }

    @Override
    public JPanel productCard(String name, LayoutManager layout) {
        JPanel panel = named(new JPanel(layout == null ? new BorderLayout() : layout), name);
        panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.setBorder(UiBorders.LINE);
        return panel;
    }

    @Override
    public JComponent stateView(String name, ShopPageState state, String message, Runnable retry) {
        var panel = named(new JPanel(new BorderLayout()), name);
        StateTheme theme = colorsByState(state);
        panel.setBackground(theme.background);
        panel.setForeground(theme.foreground);

        JLabel messageLabel = new JLabel(message);
        messageLabel.setForeground(theme.foreground);
        messageLabel.setFont(UiTypography.BODY);
        panel.add(messageLabel, BorderLayout.CENTER);

        if (retry != null) {
            JButton retryButton = secondaryButton(name + ".retry", "重试");
            retryButton.addActionListener(ignored -> retry.run());
            panel.add(retryButton, BorderLayout.SOUTH);
        }
        return panel;
    }

    /** Applies shared styling to a table with the required row and header heights. */
    public void styleTable(JTable table, boolean compact) {
        ShopComponentStyle.styleTable(table, compact);
    }

    /** Applies shared styling to a tabbed pane. */
    public void styleTabbedPane(JTabbedPane tabs) {
        ShopComponentStyle.styleTabbedPane(tabs);
    }

    /** Applies shared styling to a page panel. */
    public JPanel stylePagePanel(LayoutManager layout) {
        return ShopComponentStyle.pagePanel(layout);
    }

    /** Applies shared styling to a text-like component. */
    public <T extends JComponent> T styleText(T component) {
        return ShopComponentStyle.styleTextComponent(component);
    }

    /** Applies shared styling to a scroll pane. */
    public JScrollPane styleScrollPane(JScrollPane scrollPane) {
        return ShopComponentStyle.styleScrollPane(scrollPane);
    }

    /** Applies shared styling to dialog content container. */
    public JPanel styleDialogContent(LayoutManager layout) {
        return ShopComponentStyle.styleDialogContent(new JPanel(layout == null ? new BorderLayout() : layout));
    }

    private static JButton styleButton(JButton button, Style style, boolean boldFont) {
        button.setFocusPainted(false);
        button.setBorder(UiBorders.LINE);
        button.setOpaque(true);
        button.setFont(boldFont ? UiTypography.BODY_BOLD : UiTypography.BODY);
        button.setBackground(style.background);
        button.setForeground(style.foreground);
        Dimension preferredSize = button.getPreferredSize();
        button.setPreferredSize(new Dimension(preferredSize.width, 32));
        return button;
    }

    private static StateTheme colorsByState(ShopPageState state) {
        return switch (state) {
            case LOADING, SUBMITTING -> new StateTheme(UiColors.BACKGROUND_SUBTLE,
                    UiColors.TEXT_SECONDARY);
            case EMPTY -> new StateTheme(UiColors.BACKGROUND_PAGE, UiColors.TEXT_SECONDARY);
            case ERROR, DISCONNECTED -> new StateTheme(UiColors.ERROR_BG, UiColors.ERROR_FG);
            default -> new StateTheme(UiColors.BACKGROUND_PAGE, UiColors.TEXT_PRIMARY);
        };
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        return component;
    }

    private static final class StateTheme {
        final Color background;
        final Color foreground;

        private StateTheme(Color background, Color foreground) {
            this.background = background;
            this.foreground = foreground;
        }
    }

    private enum Style {
        PRIMARY(UiColors.ACCENT, UiColors.TEXT_ON_PRIMARY),
        NAVIGATION(UiColors.PRIMARY, UiColors.TEXT_ON_PRIMARY),
        SECONDARY(UiColors.BACKGROUND_PAGE, UiColors.PRIMARY);

        private final Color background;
        private final Color foreground;

        Style(Color background, Color foreground) {
            this.background = background;
            this.foreground = foreground;
        }
    }
}
