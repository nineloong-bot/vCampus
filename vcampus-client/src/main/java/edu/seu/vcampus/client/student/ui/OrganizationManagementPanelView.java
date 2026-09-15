package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Assembles the organization page: tree, toolbar, editor and status areas. */
abstract class OrganizationManagementPanelView extends OrganizationManagementPanelActions {

    /** Creates the view segment of the organization panel. */
    protected OrganizationManagementPanelView(StudentClientService students,
            edu.seu.vcampus.client.core.network.ClientConnection connection,
            boolean departmentManagementAllowed, boolean classManagementAllowed) {
        super(students, connection, departmentManagementAllowed, classManagementAllowed);
    }

    void buildPage() {
        JPanel left = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        left.setOpaque(false);

        JPanel heading = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        heading.setOpaque(false);
        JLabel title = new JLabel("组织架构管理");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        title.setName("student.org.title");
        heading.add(title, BorderLayout.NORTH);
        statusLabel.setFont(UiTypography.CAPTION);
        statusLabel.setForeground(UiColors.TEXT_SECONDARY);
        statusLabel.setName("student.org.status");
        heading.add(statusLabel, BorderLayout.CENTER);
        left.add(heading, BorderLayout.NORTH);

        tree.setName("student.org.tree");
        tree.getAccessibleContext().setAccessibleName("组织架构树");
        tree.setBackground(UiColors.BACKGROUND_PAGE);
        tree.setFont(UiTypography.BODY);
        tree.setCellRenderer(new OrgTreeCellRenderer());
        tree.addTreeSelectionListener(this::treeSelectionChanged);
        JScrollPane treeScroll = new JScrollPane(tree,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        treeScroll.setName("student.org.tree.scroll");
        treeScroll.setOpaque(false);
        treeScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        treeScroll.getViewport().setOpaque(false);
        treeScroll.getViewport().setBackground(UiColors.BACKGROUND_PAGE);
        left.add(treeScroll, BorderLayout.CENTER);

        JPanel treeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        treeButtons.setOpaque(false);
        addDeptButton.setName("student.org.add-dept");
        addDeptButton.setFont(UiTypography.BODY);
        addDeptButton.setEnabled(false);
        addDeptButton.addActionListener(e -> startAddDepartment());
        if (departmentManagementAllowed) treeButtons.add(addDeptButton);
        addMajorButton.setName("student.org.add-major");
        addMajorButton.setFont(UiTypography.BODY);
        addMajorButton.setEnabled(false);
        addMajorButton.addActionListener(e -> startAddMajor());
        treeButtons.add(addMajorButton);
        if (classManagementAllowed) {
            addClassButton.setName("student.org.add-class");
            addClassButton.setFont(UiTypography.BODY);
            addClassButton.setEnabled(false);
            addClassButton.addActionListener(e -> startAddClass());
            treeButtons.add(addClassButton);
            addStudentButton.setName("student.org.add-student");
            addStudentButton.setFont(UiTypography.BODY);
            addStudentButton.setEnabled(false);
            addStudentButton.addActionListener(e -> startAddStudent());
            treeButtons.add(addStudentButton);
            batchAssignButton.setName("student.org.batch-assign");
            batchAssignButton.setFont(UiTypography.BODY);
            batchAssignButton.setEnabled(false);
            batchAssignButton.addActionListener(e -> startBatchAssign());
            treeButtons.add(batchAssignButton);
        }
        left.add(treeButtons, BorderLayout.SOUTH);

        add(left, BorderLayout.WEST);

        editPanel.setOpaque(false);
        editPanel.setPreferredSize(new Dimension(360, 0));
        showPlaceholder();
        add(editPanel, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0));
        bottom.setOpaque(false);
        errorLabel.setFont(UiTypography.CAPTION);
        errorLabel.setForeground(UiColors.ERROR_FG);
        errorLabel.setName("student.org.error");
        bottom.add(errorLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }
}
