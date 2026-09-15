package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Assembles the student search page: filters, results table, pagination and detail split. */
abstract class StudentSearchPanelView extends StudentSearchPanelFilterBar {

    /** Creates the view segment of the student search panel. */
    protected StudentSearchPanelView(StudentClientService students, ClientConnection connection,
            boolean canEdit) {
        super(students, connection, canEdit);
    }

    void connectionChanged(ConnectionState state) {
        onEdt(() -> {
            if (!active) return;
            boolean connected = state == ConnectionState.CONNECTED;
            searchButton.setEnabled(connected);
            departmentCombo.setEnabled(connected);
            majorCombo.setEnabled(connected);
            classCombo.setEnabled(connected);
            statusCombo.setEnabled(connected);
            statusLabel.setText(connected ? "就绪" : "连接已断开");
        });
    }

    void buildPage() {
        JPanel leftPanel = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        leftPanel.setOpaque(false);

        JPanel heading = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        heading.setOpaque(false);
        JLabel title = new JLabel("学生查询");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        title.setName("student.search.title");
        heading.add(title, BorderLayout.NORTH);
        statusLabel = new JLabel("就绪");
        statusLabel.setFont(UiTypography.CAPTION);
        statusLabel.setForeground(UiColors.TEXT_SECONDARY);
        statusLabel.setName("student.search.feedback");
        heading.add(statusLabel, BorderLayout.SOUTH);
        JPanel filterBar = buildFilterBar();
        heading.add(filterBar, BorderLayout.CENTER);
        leftPanel.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        center.setOpaque(false);
        resultsTable = new JTable();
        resultsTable.setName("student.search.table");
        resultsTable.setModel(new DefaultTableModel(new String[]{"一卡通号", "学号", "姓名", "班级", "状态"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        });
        tableModel = (DefaultTableModel) resultsTable.getModel();
        resultsTable.setRowHeight(UiSpacing.SPACE_6);
        resultsTable.setFont(UiTypography.BODY);
        resultsTable.getTableHeader().setFont(UiTypography.SECTION_TITLE);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(new EmptyBorder(UiSpacing.SPACE_1, UiSpacing.SPACE_2, UiSpacing.SPACE_1, UiSpacing.SPACE_2));
                if (!isSelected) setBackground(row % 2 == 0 ? UiColors.BACKGROUND_PAGE : UiColors.BACKGROUND_SUBTLE);
                return this;
            }
        });
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            onStudentSelected();
        });
        resultsScrollPane = new JScrollPane(resultsTable,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        resultsScrollPane.setName("student.search.table.scroll");
        resultsScrollPane.setBorder(UiBorders.LINE);
        resultsScrollPane.getViewport().setBackground(UiColors.BACKGROUND_PAGE);
        emptyLabel = new JLabel("输入关键词并点击搜索", SwingConstants.CENTER);
        emptyLabel.setFont(UiTypography.SECTION_TITLE);
        emptyLabel.setForeground(UiColors.TEXT_SECONDARY);
        emptyLabel.setName("student.search.empty");
        JPanel tableArea = new JPanel(new BorderLayout());
        tableArea.setOpaque(false);
        tableArea.add(resultsScrollPane, BorderLayout.CENTER);
        tableArea.add(emptyLabel, BorderLayout.SOUTH);
        emptyLabel.setVisible(true);
        resultsScrollPane.setVisible(false);

        JPanel paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, UiSpacing.SPACE_3, UiSpacing.SPACE_2));
        paginationPanel.setOpaque(false);
        prevButton = new JButton("上一页");
        prevButton.setName("student.search.prev");
        prevButton.setFont(UiTypography.BODY);
        prevButton.setEnabled(false);
        prevButton.addActionListener(e -> changePage(-1));
        pageInfoLabel = new JLabel("第0页/共0条");
        pageInfoLabel.setFont(UiTypography.BODY);
        pageInfoLabel.setForeground(UiColors.TEXT_PRIMARY);
        pageInfoLabel.setName("student.search.page");
        nextButton = new JButton("下一页");
        nextButton.setName("student.search.next");
        nextButton.setFont(UiTypography.BODY);
        nextButton.setEnabled(false);
        nextButton.addActionListener(e -> changePage(1));
        paginationPanel.add(prevButton);
        paginationPanel.add(pageInfoLabel);
        paginationPanel.add(nextButton);
        center.add(tableArea, BorderLayout.CENTER);
        center.add(paginationPanel, BorderLayout.SOUTH);
        leftPanel.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0));
        bottom.setOpaque(false);
        errorLabel = new JLabel(" ");
        errorLabel.setFont(UiTypography.CAPTION);
        errorLabel.setForeground(UiColors.ERROR_FG);
        errorLabel.setName("student.search.error");
        bottom.add(errorLabel, BorderLayout.WEST);
        leftPanel.add(bottom, BorderLayout.SOUTH);

        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.setOpaque(false);
        JLabel placeholderLabel = new JLabel("点击学生查看详情", SwingConstants.CENTER);
        placeholderLabel.setFont(UiTypography.SECTION_TITLE);
        placeholderLabel.setForeground(UiColors.TEXT_SECONDARY);
        placeholder.add(placeholderLabel, BorderLayout.CENTER);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, placeholder);
        splitPane.setDividerSize(6);
        splitPane.setResizeWeight(0.55);
        splitPane.setOneTouchExpandable(true);
        splitPane.setBorder(null);
        add(splitPane, BorderLayout.CENTER);
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.55));
    }
}
