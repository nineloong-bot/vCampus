package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/** Renders organization tree nodes with department, major, year and class labels. */
final class OrgTreeCellRenderer extends DefaultTreeCellRenderer {
    @Override public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if (value instanceof DefaultMutableTreeNode node) {
            Object obj = node.getUserObject();
            if (obj instanceof DepartmentView dept) setText(dept.code() + " - " + dept.name());
            else if (obj instanceof MajorView major) setText(major.code() + " - " + major.name());
            else if (obj instanceof YearNode year) setText(year.displayName());
            else if (obj instanceof ClassView cls) setText(cls.code() + " - " + cls.name());
        }
        if (selected) {
            setBackgroundSelectionColor(UiColors.PRIMARY);
            setForeground(UiColors.TEXT_ON_PRIMARY);
        } else {
            setBackgroundNonSelectionColor(UiColors.BACKGROUND_PAGE);
            setForeground(UiColors.TEXT_PRIMARY);
        }
        setBackgroundSelectionColor(UiColors.PRIMARY);
        setBackgroundNonSelectionColor(UiColors.BACKGROUND_PAGE);
        return this;
    }
}
