package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Page layout for the batch assignment segments. */
abstract class BatchClassAssignmentDialogLayout extends BatchClassAssignmentDialogImporting {

    BatchClassAssignmentDialogLayout(Window owner, StudentClientService students,
            MajorView major, List<ClassView> classes) {
        super(owner, students, major, classes);
    }

    @Override
    void initializeDialog(Window owner) {
        setContentPane(build());
        setSize(960, 720);
        setLocationRelativeTo(owner);
    }

    private JPanel build() {
        JPanel page = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        page.setBackground(UiColors.BACKGROUND_PAGE);
        page.setBorder(UiBorders.pageInset());

        // Heading
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_1));
        heading.setOpaque(false);
        JLabel title = new JLabel("批量分班 — " + major.name());
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        heading.add(title);
        JLabel hint = new JLabel("CSV格式: 姓名,一卡通号,性别,综合成绩  |  初始密码: 12345678");
        hint.setFont(UiTypography.CAPTION);
        hint.setForeground(UiColors.TEXT_SECONDARY);
        heading.add(hint);
        page.add(heading, BorderLayout.NORTH);

        // Center: file chooser + table + stats
        JPanel center = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        center.setOpaque(false);

        // File chooser row
        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        fileRow.setOpaque(false);
        JButton chooseFile = new JButton("选择CSV文件");
        chooseFile.setFont(UiTypography.BODY);
        chooseFile.addActionListener(e -> chooseFile());
        fileRow.add(chooseFile);
        fileLabel = new JLabel("未选择文件");
        fileLabel.setFont(UiTypography.BODY);
        fileLabel.setForeground(UiColors.TEXT_SECONDARY);
        fileRow.add(fileLabel);
        center.add(fileRow, BorderLayout.NORTH);

        // Preview table
        tableModel = new BatchTableModel();
        previewTable = new JTable(tableModel);
        previewTable.setName("student.batch.table");
        previewTable.setFont(UiTypography.BODY);
        previewTable.setRowHeight(28);
        previewTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        previewTable.getTableHeader().setFont(UiTypography.CAPTION);
        previewTable.getTableHeader().setReorderingAllowed(false);
        previewTable.setDefaultRenderer(Double.class, new ScoreRenderer());
        previewTable.setDefaultRenderer(Integer.class, new ScoreRenderer());

        // Class column with combo box editor
        if (!availableClasses.isEmpty()) {
            previewTable.getColumnModel().getColumn(4).setCellEditor(new ClassComboEditor());
        }
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane tableScroll = new JScrollPane(previewTable);
        tableScroll.setPreferredSize(new Dimension(0, 350));
        center.add(tableScroll, BorderLayout.CENTER);

        // Stats panel
        statsPanel = new JPanel(new GridLayout(1, 0, UiSpacing.SPACE_3, 0));
        statsPanel.setOpaque(false);
        center.add(statsPanel, BorderLayout.SOUTH);

        page.add(center, BorderLayout.CENTER);

        // Bottom: error + buttons
        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0));
        bottom.setOpaque(false);
        errorLabel = new JLabel(" ");
        errorLabel.setName("student.batch.error");
        errorLabel.setForeground(UiColors.ERROR_FG);
        errorLabel.setFont(UiTypography.CAPTION);
        bottom.add(errorLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        buttons.setOpaque(false);
        assignButton = new JButton("自动分配");
        assignButton.setFont(UiTypography.BODY);
        assignButton.setEnabled(false);
        assignButton.addActionListener(e -> autoAssign());
        buttons.add(assignButton);
        JButton cancel = new JButton("取消");
        cancel.setFont(UiTypography.BODY);
        cancel.addActionListener(e -> dispose());
        buttons.add(cancel);
        importButton = new JButton("确认导入");
        importButton.setFont(UiTypography.BODY);
        importButton.setEnabled(false);
        importButton.addActionListener(e -> doImport());
        buttons.add(importButton);
        bottom.add(buttons, BorderLayout.EAST);
        page.add(bottom, BorderLayout.SOUTH);

        return page;
    }
}
