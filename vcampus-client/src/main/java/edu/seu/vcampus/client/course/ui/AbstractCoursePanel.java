package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/** Shared course-page composition that consumes the teammate-owned UI tokens. */
abstract class AbstractCoursePanel extends JPanel {
    enum ViewState { INITIAL, LOADING, NORMAL, EMPTY, ERROR, DISCONNECTED, SUBMITTING, CONFLICT }

    protected final JPanel body = new JPanel(new BorderLayout(0, UiSpacing.LG));
    private final JLabel stateNotice = label("", UiTypography.BODY, UiColors.TEXT_PRIMARY);
    private volatile ViewState viewState = ViewState.INITIAL;
    private final UiAsyncGuard asyncGuard = new UiAsyncGuard();
    private boolean reloadWhenShown;
    private final JLabel breadcrumbTitle;
    private final JLabel pageTitle;

    protected AbstractCoursePanel(String title, String description) {
        super(new BorderLayout(0, UiSpacing.XL));
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(BorderFactory.createEmptyBorder(UiSpacing.PAGE_PADDING, UiSpacing.PAGE_PADDING,
                UiSpacing.PAGE_PADDING, UiSpacing.PAGE_PADDING));
        body.setOpaque(false);
        breadcrumbTitle = label("课程中心  /  " + title, UiTypography.CAPTION, UiColors.TEXT_SECONDARY);
        pageTitle = label(title, UiTypography.PAGE_TITLE, UiColors.TEXT_PRIMARY);
        add(heading(description), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        stateNotice.setOpaque(true);
        stateNotice.setBackground(UiColors.BACKGROUND_SUBTLE);
        stateNotice.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.MD, UiSpacing.LG, UiSpacing.MD, UiSpacing.LG)));
        stateNotice.setVisible(false);
        add(stateNotice, BorderLayout.SOUTH);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentHidden(ComponentEvent event) {
                asyncGuard.deactivate();
                reloadWhenShown = true;
            }

            @Override public void componentShown(ComponentEvent event) {
                asyncGuard.activate();
                if (reloadWhenShown) {
                    reloadWhenShown = false;
                    refreshAfterNavigation();
                }
            }
        });
    }

    /** Reloads authoritative data after CardLayout navigation makes this page visible again. */
    protected void refreshAfterNavigation() { }

    final ViewState viewState() {
        return viewState;
    }

    protected final long beginAsyncRequest() {
        return asyncGuard.begin();
    }

    protected final boolean acceptsAsyncResult(long request) {
        return asyncGuard.accepts(request);
    }

    @Override public void addNotify() {
        super.addNotify();
        asyncGuard.activate();
    }

    @Override public void removeNotify() {
        asyncGuard.deactivate();
        super.removeNotify();
    }

    protected final void showState(ViewState state, String message) {
        ViewState previous = viewState;
        viewState = state;
        stateNotice.setText(message == null ? "" : message);
        stateNotice.setVisible(state != ViewState.INITIAL && state != ViewState.NORMAL);
        firePropertyChange("course.viewState", previous, state);
    }

    protected final void setPageTitle(String title) {
        breadcrumbTitle.setText("课程中心  /  " + title);
        pageTitle.setText(title);
    }

    protected final void setPageTitleFont(Font font) {
        pageTitle.setFont(font);
    }

    private JPanel heading(String description) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(breadcrumbTitle);
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(pageTitle);
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(label(description, UiTypography.BODY, UiColors.TEXT_SECONDARY));
        return panel;
    }

    protected static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    protected static JButton primary(String text) {
        JButton button = button(text);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(UiColors.ACCENT);
        button.setForeground(UiColors.TEXT_ON_PRIMARY);
        button.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.LG, 0, UiSpacing.LG));
        return button;
    }

    protected static JButton secondary(String text) {
        JButton button = button(text);
        button.setBackground(UiColors.BACKGROUND_PAGE);
        button.setForeground(UiColors.PRIMARY);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiColors.PRIMARY),
                BorderFactory.createEmptyBorder(0, UiSpacing.LG, 0, UiSpacing.LG)));
        return button;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(UiTypography.BODY_BOLD);
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, UiDimensions.CONTROL_HEIGHT));
        button.setMargin(new Insets(0, UiSpacing.LG, 0, UiSpacing.LG));
        button.setFocusPainted(true);
        button.getAccessibleContext().setAccessibleName(text);
        return button;
    }

    protected static JTable table(Object[][] rows, Object[] columns) {
        JTable table = new JTable(rows, columns);
        table.setFont(UiTypography.BODY);
        table.setForeground(UiColors.TEXT_PRIMARY);
        table.setBackground(UiColors.BACKGROUND_PAGE);
        table.setRowHeight(UiDimensions.TABLE_ROW_HEIGHT);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(UiColors.BORDER_DEFAULT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(UiTypography.BODY_BOLD);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getTableHeader().setForeground(UiColors.TEXT_PRIMARY);
        table.getTableHeader().setPreferredSize(new Dimension(0, UiDimensions.TABLE_ROW_HEIGHT));
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                JLabel cell = (JLabel) super.getTableCellRendererComponent(
                        source, value, selected, focused, row, column);
                cell.setToolTipText(value == null ? null : value.toString());
                return cell;
            }
        };
        renderer.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.SM, 0, UiSpacing.SM));
        table.setDefaultRenderer(Object.class, renderer);
        table.getAccessibleContext().setAccessibleName("课程数据表格");
        return table;
    }
}
