package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.core.ui.theme.*;

import javax.swing.*;
import java.awt.*;
import javax.swing.table.DefaultTableModel;
import java.util.concurrent.atomic.AtomicLong;

/** Shared query/management page structure for the library workspace. */
class LibraryDataPanel extends JPanel {
    protected final JLabel status = new JLabel("尚未加载", JLabel.CENTER);
    protected final JTable table;
    private final AtomicLong lifecycle = new AtomicLong();
    private volatile boolean active = true;

    LibraryDataPanel(String name, String title, String description, String... columns) {
        super(new BorderLayout(0, UiSpacing.SPACE_4));
        setName(name);
        setBackground(LibraryPalette.PAGE);
        setBorder(UiBorders.pageInset());
        getAccessibleContext().setAccessibleName(title);
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_1));
        heading.setOpaque(false);
        JLabel breadcrumb = new JLabel("虚拟校园 / 图书借阅 / " + title);
        breadcrumb.setFont(LibraryPalette.CAPTION);
        breadcrumb.setForeground(LibraryPalette.MUTED);
        JLabel headingLabel = new JLabel(title);
        headingLabel.setFont(LibraryPalette.TITLE);
        headingLabel.setForeground(LibraryPalette.TEXT);
        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(LibraryPalette.BODY);
        descriptionLabel.setForeground(LibraryPalette.MUTED);
        heading.add(breadcrumb);
        heading.add(headingLabel);
        heading.add(descriptionLabel);
        add(heading, BorderLayout.NORTH);
        table = new JTable(new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        });
        table.setRowHeight(UiDimensions.TABLE_ROW_HEIGHT);
        table.setFillsViewportHeight(true);
        table.setBackground(LibraryPalette.SURFACE);
        table.setForeground(LibraryPalette.TEXT);
        table.setFont(LibraryPalette.BODY);
        table.setSelectionBackground(LibraryPalette.SELECTION);
        table.setSelectionForeground(LibraryPalette.TEXT);
        table.setGridColor(LibraryPalette.BORDER);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setBackground(LibraryPalette.SUBTLE);
        table.getTableHeader().setForeground(LibraryPalette.MUTED);
        table.getTableHeader().setFont(LibraryPalette.BODY.deriveFont(Font.BOLD));
        table.getTableHeader().setPreferredSize(new Dimension(0, UiDimensions.TABLE_ROW_HEIGHT));
        status.setFont(LibraryPalette.BODY);
        status.setForeground(LibraryPalette.MUTED);
        status.setBorder(BorderFactory.createEmptyBorder(UiSpacing.SPACE_3, UiSpacing.SPACE_4,
                UiSpacing.SPACE_3, UiSpacing.SPACE_4));
        JPanel results = new JPanel(new BorderLayout());
        results.setBackground(LibraryPalette.SURFACE);
        results.setBorder(BorderFactory.createLineBorder(LibraryPalette.BORDER));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(LibraryPalette.SURFACE);
        results.add(scroll, BorderLayout.CENTER);
        results.add(status, BorderLayout.SOUTH);
        add(results, BorderLayout.CENTER);
    }

    protected final long beginRequest() { return lifecycle.incrementAndGet(); }
    protected final boolean accepts(long request) { return active && lifecycle.get() == request; }

    @Override public void addNotify() { active = true; super.addNotify(); }
    @Override public void removeNotify() { active = false; lifecycle.incrementAndGet(); super.removeNotify(); }
}
